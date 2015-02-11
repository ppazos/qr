package pdg2009;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

import org.dcm4che2.data.BasicDicomObject;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.UID;
import org.dcm4che2.data.UIDDictionary;
import org.dcm4che2.data.VR;
import org.dcm4che2.io.DicomOutputStream;
import org.dcm4che2.net.Association;
import org.dcm4che2.net.CommandUtils;
import org.dcm4che2.net.ConfigurationException;
import org.dcm4che2.net.Device;
import org.dcm4che2.net.DicomServiceException;
import org.dcm4che2.net.DimseRSP;
import org.dcm4che2.net.DimseRSPHandler;
import org.dcm4che2.net.ExtQueryTransferCapability;
import org.dcm4che2.net.ExtRetrieveTransferCapability;
import org.dcm4che2.net.NetworkApplicationEntity;
import org.dcm4che2.net.NetworkConnection;
import org.dcm4che2.net.NewThreadExecutor;
import org.dcm4che2.net.NoPresentationContextException;
import org.dcm4che2.net.PDVInputStream;
import org.dcm4che2.net.Status;
import org.dcm4che2.net.TransferCapability;
import org.dcm4che2.net.service.DicomService;
import org.dcm4che2.net.service.StorageService;
import org.dcm4che2.tool.dcmqr.DcmQR;

import pdg2009.QueryRetrieveLevel.QueryRetrieveLevelEnum;

public class QRLifecycle {

//    private static Logger LOG = LoggerFactory.getLogger(QRLifecycle.class);

    private boolean noExtNegotiation;
    
    private String keyStoreURL = "resource:tls/test_sys_1.p12";
    
    private static char[] SECRET = { 's', 'e', 'c', 'r', 'e', 't' };
    
    private char[] keyStorePassword = SECRET; 

    private char[] keyPassword; 
    
    private String trustStoreURL = "resource:tls/mesa_certs.jks";
    
    private char[] trustStorePassword = SECRET;
    
    private static final String[] EMPTY_STRING = {};
    
    private static final String[] IVRLE_TS = {
        UID.ImplicitVRLittleEndian };

    private static final String[] NATIVE_LE_TS = {
        UID.ExplicitVRLittleEndian,
        UID.ImplicitVRLittleEndian};

    private static final String[] NATIVE_BE_TS = {
        UID.ExplicitVRBigEndian,
        UID.ImplicitVRLittleEndian};

    private static final String[] DEFLATED_TS = {
        UID.DeflatedExplicitVRLittleEndian,
        UID.ExplicitVRLittleEndian,
        UID.ImplicitVRLittleEndian};

    private static final String[] NOPX_TS = {
        UID.NoPixelData,
        UID.ExplicitVRLittleEndian,
        UID.ImplicitVRLittleEndian};

    private static final String[] NOPXDEFL_TS = {
        UID.NoPixelDataDeflate,
        UID.NoPixelData,
        UID.ExplicitVRLittleEndian,
        UID.ImplicitVRLittleEndian};
    
    private NetworkConnection conn = new NetworkConnection();
    private NetworkConnection remoteConn = new NetworkConnection();
    
    private Executor executor = new NewThreadExecutor("DCMQR");
    private Association assoc;
    
    private NetworkApplicationEntity remoteAE = new NetworkApplicationEntity();
    private NetworkApplicationEntity ae = new NetworkApplicationEntity();
    
    private int priority = 0;
    private DicomObject keys = new BasicDicomObject();
    private int cancelAfter = Integer.MAX_VALUE;
    private QueryRetrieveLevelEnum qrlevel = QueryRetrieveLevel.QueryRetrieveLevelEnum.STUDY;
    private List<String> privateFind = new ArrayList<String>();
    
    private int completed;
    private int warning;
    private int failed;
    
    private boolean relationQR;
    private boolean dateTimeMatching;
    private boolean fuzzySemanticPersonNameMatching;
    
    private boolean cget;
    private String moveDest;
    private boolean evalRetrieveAET = false;
    
    private File storeDest;
    private boolean devnull;
    private int fileBufferSize = 256;
    
    private final List<TransferCapability> storeTransferCapability =
            new ArrayList<TransferCapability>(8);
    
    private Device device = new Device("DCMQR");
    
    public QRLifecycle() {
        remoteAE.setInstalled(true);
        remoteAE.setAssociationAcceptor(true);
        remoteAE.setNetworkConnection(new NetworkConnection[] { remoteConn });

        device.setNetworkApplicationEntity(ae);
        device.setNetworkConnection(conn);
        ae.setNetworkConnection(conn);
        ae.setAssociationInitiator(true);
        ae.setAssociationAcceptor(true);
        ae.setAETitle("DCMQR");
    }
    
    public void start() throws IOException
    {
        if (conn.isListening())
        {
            conn.bind(executor);
            System.out.println("Start Server listening on port "
                    + conn.getPort());
        }
    }

    public void stop()
    {
        if (conn.isListening())
        {
            conn.unbind();
        }
    }

    public void open() throws IOException, ConfigurationException, InterruptedException
    {
        assoc = ae.connect(remoteAE, executor);
    }
    
    public void close() throws InterruptedException
    {
        assoc.release(true);
    }

    public List<DicomObject> query() throws IOException, InterruptedException
    {
        List<DicomObject> result = new ArrayList<DicomObject>();
        TransferCapability tc = selectFindTransferCapability();
        String cuid = tc.getSopClass();
        String tsuid = selectTransferSyntax(tc);
        if (tc.getExtInfoBoolean(ExtQueryTransferCapability.RELATIONAL_QUERIES)
                || containsUpperLevelUIDs(cuid))
        {
//            LOG.info("Send Query Request using {}:\n{}", UIDDictionary.getDictionary().prompt(cuid), keys);
            DimseRSP rsp = assoc.cfind(cuid, priority, keys, tsuid, cancelAfter);
            while (rsp.next())
            {
                DicomObject cmd = rsp.getCommand();
                if (CommandUtils.isPending(cmd))
                {
                    DicomObject data = rsp.getDataset();
                    result.add(data);
//                    LOG.info("Query Response #{}:\n{}", Integer.valueOf(result.size()), data);
                }
            }
        } else
        {
            List<DicomObject> upperLevelUIDs = queryUpperLevelUIDs(cuid, tsuid);
            List<DimseRSP> rspList = new ArrayList<DimseRSP>(upperLevelUIDs.size());
            for (int i = 0, n = upperLevelUIDs.size(); i < n; i++)
            {
                upperLevelUIDs.get(i).copyTo(keys);
//                LOG.info("Send Query Request #{}/{} using {}:\n{}",
//                        new Object[] { Integer.valueOf(i + 1),
//                                Integer.valueOf(n),
//                                UIDDictionary.getDictionary().prompt(cuid),
//                                keys });
                rspList.add(assoc.cfind(cuid, priority, keys, tsuid, cancelAfter));
            }
            for (int i = 0, n = rspList.size(); i < n; i++)
            {
                DimseRSP rsp = rspList.get(i);
                for (int j = 0; rsp.next(); ++j)
                {
                    DicomObject cmd = rsp.getCommand();
                    if (CommandUtils.isPending(cmd))
                    {
                        DicomObject data = rsp.getDataset();
                        result.add(data);
//                        LOG.info("Query Response #{} for Query Request #{}/{}:\n{}",
//                                 new Object[] { Integer.valueOf(j + 1),
//                                                Integer.valueOf(i + 1),
//                                                Integer.valueOf(n), data });
                    }
                }
            }
        }
        return result;
    }
    
    private List<DicomObject> queryUpperLevelUIDs(String cuid, String tsuid)
    throws IOException, InterruptedException
    {
        List<DicomObject> keylist = new ArrayList<DicomObject>();
        if (Arrays.asList(QueryRetrieveLevel.PATIENT_LEVEL_FIND_CUID).contains(cuid)) {
            queryPatientIDs(cuid, tsuid, keylist);
            if (qrlevel == QueryRetrieveLevel.QueryRetrieveLevelEnum.STUDY) {
                return keylist;
            }
            keylist = queryStudyOrSeriesIUIDs(cuid, tsuid, keylist,
                    Tag.StudyInstanceUID, QueryRetrieveLevel.STUDY_MATCHING_KEYS, QueryRetrieveLevel.QueryRetrieveLevelEnum.STUDY);
        } else {
            keylist.add(new BasicDicomObject());
            keylist = queryStudyOrSeriesIUIDs(cuid, tsuid, keylist,
                    Tag.StudyInstanceUID, QueryRetrieveLevel.PATIENT_STUDY_MATCHING_KEYS, QueryRetrieveLevel.QueryRetrieveLevelEnum.STUDY);
        }
        if (qrlevel == QueryRetrieveLevel.QueryRetrieveLevelEnum.IMAGE) {
            keylist = queryStudyOrSeriesIUIDs(cuid, tsuid, keylist,
                    Tag.SeriesInstanceUID, QueryRetrieveLevel.SERIES_MATCHING_KEYS, QueryRetrieveLevel.QueryRetrieveLevelEnum.SERIES);
        }
        return keylist;
    }
    
    private void queryPatientIDs(String cuid, String tsuid,
            List<DicomObject> keylist) throws IOException, InterruptedException
    {
        String patID = keys.getString(Tag.PatientID);
        String issuer = keys.getString(Tag.IssuerOfPatientID);
        if (patID != null)
        {
            DicomObject patIdKeys = new BasicDicomObject();
            patIdKeys.putString(Tag.PatientID, VR.LO, patID);
            if (issuer != null)
            {
                patIdKeys.putString(Tag.IssuerOfPatientID, VR.LO, issuer);
            }
            keylist.add(patIdKeys);
        }
        else
        {
            DicomObject patLevelQuery = new BasicDicomObject();
            keys.subSet(QueryRetrieveLevel.PATIENT_MATCHING_KEYS).copyTo(patLevelQuery);
            patLevelQuery.putNull(Tag.PatientID, VR.LO);
            patLevelQuery.putNull(Tag.IssuerOfPatientID, VR.LO);
            patLevelQuery.putString(Tag.QueryRetrieveLevel, VR.CS, "PATIENT");
            
//            LOG.info("Send Query Request using {}:\n{}", UIDDictionary.getDictionary().prompt(cuid), patLevelQuery);
            
            DimseRSP rsp = assoc.cfind(cuid, priority, patLevelQuery, tsuid, Integer.MAX_VALUE);
            for (int i = 0; rsp.next(); ++i)
            {
                DicomObject cmd = rsp.getCommand();
                if (CommandUtils.isPending(cmd))
                {
                    DicomObject data = rsp.getDataset();
                    
//                    LOG.info("Query Response #{}:\n{}", Integer.valueOf(i+1), data);
                    
                    DicomObject patIdKeys = new BasicDicomObject();
                    patIdKeys.putString(Tag.PatientID, VR.LO, data.getString(Tag.PatientID));
                    issuer = keys.getString(Tag.IssuerOfPatientID);
                    if (issuer != null)
                    {
                        patIdKeys.putString(Tag.IssuerOfPatientID, VR.LO, issuer);
                    }
                    keylist.add(patIdKeys);
                }
            }
        }
    }
    
    public final void disableSSLv2Hello() {
        conn.disableSSLv2Hello();
    }

    public final void setTlsNeedClientAuth(boolean needClientAuth) {
        conn.setTlsNeedClientAuth(needClientAuth);
    }

    public final void setKeyStoreURL(String url) {
        keyStoreURL = url;
    }
    
    public final void setKeyStorePassword(String pw) {
        keyStorePassword = pw.toCharArray();
    }
    
    public final void setKeyPassword(String pw) {
        keyPassword = pw.toCharArray();
    }
    
    public final void setTrustStorePassword(String pw) {
        trustStorePassword = pw.toCharArray();
    }
    
    public final void setTrustStoreURL(String url) {
        trustStoreURL = url;
    }
    
    public void setNoExtNegotiation(boolean b) {
        this.noExtNegotiation = b;
    }

    public void setFuzzySemanticPersonNameMatching(boolean b) {
        this.fuzzySemanticPersonNameMatching = b;
    }

    public void setDateTimeMatching(boolean b) {
        this.dateTimeMatching = b;
    }

    public void setRelationQR(boolean b) {
        this.relationQR = b;
    }
    
    public TransferCapability selectFindTransferCapability()
    throws NoPresentationContextException
    {
        TransferCapability tc;
        if ((tc = selectTransferCapability(privateFind)) != null)
            return tc;
        if ((tc = selectTransferCapability(qrlevel.getFindClassUids())) != null)
            return tc;
        throw new NoPresentationContextException(UIDDictionary.getDictionary()
                .prompt(qrlevel.getFindClassUids()[0]) + " not supported by " + remoteAE.getAETitle());
    }
        
    public String selectTransferSyntax(TransferCapability tc)
    {
        String[] tcuids = tc.getTransferSyntax();
        if (Arrays.asList(tcuids).indexOf(UID.DeflatedExplicitVRLittleEndian) != -1)
            return UID.DeflatedExplicitVRLittleEndian;
        return tcuids[0];
    }
    
    public TransferCapability selectTransferCapability(String[] cuid) {
        TransferCapability tc;
        for (int i = 0; i < cuid.length; i++) {
            tc = assoc.getTransferCapabilityAsSCU(cuid[i]);
            if (tc != null)
                return tc;
        }
        return null;
    }

    public TransferCapability selectTransferCapability(List<String> cuid) {
        TransferCapability tc;
        for (int i = 0, n = cuid.size(); i < n; i++) {
            tc = assoc.getTransferCapabilityAsSCU(cuid.get(i));
            if (tc != null)
                return tc;
        }
        return null;
    }
    
    @SuppressWarnings("fallthrough")
    private boolean containsUpperLevelUIDs(String cuid) {
        switch (qrlevel) {
        case IMAGE:
            if (!keys.containsValue(Tag.SeriesInstanceUID)) {
                return false;
            }
            // fall through
        case SERIES:
            if (!keys.containsValue(Tag.StudyInstanceUID)) {
                return false;
            }
            // fall through
        case STUDY:
            if (Arrays.asList(QueryRetrieveLevel.PATIENT_LEVEL_FIND_CUID).contains(cuid)
                    && !keys.containsValue(Tag.PatientID)) {
                return false;
            }
            // fall through
        case PATIENT:
            // fall through
        }
        return true;
    }
    
    private List<DicomObject> queryStudyOrSeriesIUIDs(String cuid, String tsuid,
            List<DicomObject> upperLevelIDs, int uidTag, int[] matchingKeys,
            QueryRetrieveLevelEnum qrLevel) throws IOException,
            //QueryRetrieveLevel qrLevel) throws IOException,
            InterruptedException
    {
        
        List<DicomObject> keylist = new ArrayList<DicomObject>();
        String uid = keys.getString(uidTag);
        for (DicomObject upperLevelID : upperLevelIDs)
        {
            if (uid != null)
            {
                DicomObject suidKey = new BasicDicomObject();
                upperLevelID.copyTo(suidKey);
                suidKey.putString(uidTag, VR.UI, uid);
                keylist.add(suidKey);
            }
            else
            {
                DicomObject keys2 = new BasicDicomObject();
                keys.subSet(matchingKeys).copyTo(keys2);
                upperLevelID.copyTo(keys2);
                keys2.putNull(uidTag, VR.UI);
                keys2.putString(Tag.QueryRetrieveLevel, VR.CS, qrLevel.getCode());
                
//                LOG.info("Send Query Request using {}:\n{}", UIDDictionary.getDictionary().prompt(cuid), keys2);
                
                DimseRSP rsp = assoc.cfind(cuid, priority, keys2, tsuid, Integer.MAX_VALUE);
                
                for (int i = 0; rsp.next(); ++i)
                {
                    DicomObject cmd = rsp.getCommand();
                    if (CommandUtils.isPending(cmd))
                    {
                        DicomObject data = rsp.getDataset();
//                        LOG.info("Query Response #{}:\n{}", Integer.valueOf(i+1), data);
                        DicomObject suidKey = new BasicDicomObject();
                        upperLevelID.copyTo(suidKey);
                        suidKey.putString(uidTag, VR.UI, data.getString(uidTag));
                        keylist.add(suidKey);
                    }
                }
            }
        }
        return keylist;
    }
    
    public final void setCalledAET(String called, boolean reuse)
    {
        remoteAE.setAETitle(called);
        if (reuse)
            ae.setReuseAssocationToAETitle(new String[] { called });
    }
    
    public final void setLocalHost(String hostname) {
        conn.setHostname(hostname);
    }

    public final void setLocalPort(int port) {
        conn.setPort(port);
    }

    public final void setRemoteHost(String hostname) {
        remoteConn.setHostname(hostname);
    }

    public final void setRemotePort(int port) {
        remoteConn.setPort(port);
    }
    
    public final void setCalling(String calling) {
        ae.setAETitle(calling);
    }
    
    public final void addPrivate(String cuid) {
        privateFind.add(cuid);
    }
    
    public void setQueryLevel(QueryRetrieveLevelEnum qrlevel)
    {
        this.qrlevel = qrlevel;
        keys.putString(Tag.QueryRetrieveLevel, VR.CS, qrlevel.getCode());
        for (int tag : qrlevel.getReturnKeys())
        {
            keys.putNull(tag, null);
        }
    }
    
    public void addMatchingKey(int[] tagPath, String value) {
        keys.putString(tagPath, null, value);
    }

    public void addReturnKey(int[] tagPath) {
        keys.putNull(tagPath, null);
    }
    
    public void setMoveDest(String aet) {
        moveDest = aet;
    }
    
    public boolean isCMove() {
        return moveDest != null;
    }
    
    public void setCGet(boolean cget) {
        this.cget = cget;
    }

    public boolean isCGet() {
        return cget;
    }
    
    public void setEvalRetrieveAET(boolean evalRetrieveAET) {
        this.evalRetrieveAET = evalRetrieveAET;
     }
    
    public boolean isEvalRetrieveAET() {
        return evalRetrieveAET;
    }
    
    private boolean containsMoveDest(String[] retrieveAETs) {
        if (retrieveAETs != null) {
            for (String aet : retrieveAETs) {
                if (moveDest.equals(aet)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    protected void onMoveRSP(Association as, DicomObject cmd, DicomObject data) {
        if (!CommandUtils.isPending(cmd)) {
            completed += cmd.getInt(Tag.NumberOfCompletedSuboperations);
            warning += cmd.getInt(Tag.NumberOfWarningSuboperations);
            failed += cmd.getInt(Tag.NumberOfFailedSuboperations);
        }

    }
    
    private final DimseRSPHandler rspHandler = new DimseRSPHandler() {
        @Override
        public void onDimseRSP(Association as, DicomObject cmd,
                DicomObject data) {
            QRLifecycle.this.onMoveRSP(as, cmd, data);
        }
    };
    
    public void move(List<DicomObject> findResults)
    throws IOException, InterruptedException
    {
        if (moveDest == null)
            throw new IllegalStateException("moveDest == null");
        TransferCapability tc = selectTransferCapability(qrlevel.getMoveClassUids());
        if (tc == null)
            throw new NoPresentationContextException(UIDDictionary
                    .getDictionary().prompt(qrlevel.getMoveClassUids()[0])
                    + " not supported by " + remoteAE.getAETitle());
        String cuid = tc.getSopClass();
        String tsuid = selectTransferSyntax(tc);
        for (int i = 0, n = Math.min(findResults.size(), cancelAfter); i < n; ++i) {
            DicomObject keys = findResults.get(i).subSet(QueryRetrieveLevel.MOVE_KEYS);
            if (isEvalRetrieveAET() && containsMoveDest( findResults.get(i).getStrings(Tag.RetrieveAETitle)))
            {
//                LOG.info("Skipping {}:\n{}", UIDDictionary.getDictionary().prompt(cuid), keys);
            }
            else
            {
//                LOG.info("Send Retrieve Request using {}:\n{}", UIDDictionary.getDictionary().prompt(cuid), keys);
                assoc.cmove(cuid, priority, keys, tsuid, moveDest, rspHandler);
            }
        }
        assoc.waitForDimseRSP();
    }
    
    public void get(List<DicomObject> findResults)
    throws IOException, InterruptedException
    {
        TransferCapability tc = selectTransferCapability(qrlevel.getGetClassUids());
        if (tc == null)
            throw new NoPresentationContextException(UIDDictionary
                    .getDictionary().prompt(qrlevel.getGetClassUids()[0])
                    + " not supported by " + remoteAE.getAETitle());
        String cuid = tc.getSopClass();
        String tsuid = selectTransferSyntax(tc);
        for (int i = 0, n = Math.min(findResults.size(), cancelAfter); i < n; ++i) {
            DicomObject keys = findResults.get(i).subSet(QueryRetrieveLevel.MOVE_KEYS);
//            LOG.info("Send Retrieve Request using {}:\n{}", UIDDictionary.getDictionary().prompt(cuid), keys);
            assoc.cget(cuid, priority, keys, tsuid, rspHandler);
        }
        assoc.waitForDimseRSP();
    }
    
    public final int getFailed() {
        return failed;
    }

    public final int getWarning() {
        return warning;
    }

    public final int getTotalRetrieved() {
        return completed + warning;
    }
    
    public void configureTransferCapability(boolean ivrle) {
        String[] findcuids = qrlevel.getFindClassUids();
        String[] movecuids = moveDest != null ? qrlevel.getMoveClassUids()
                : EMPTY_STRING;
        String[] getcuids = cget ? qrlevel.getGetClassUids()
                : EMPTY_STRING;
        TransferCapability[] tcs = new TransferCapability[findcuids.length
                + privateFind.size() + movecuids.length + getcuids.length
                + storeTransferCapability.size()];
        int i = 0;
        for (String cuid : findcuids)
            tcs[i++] = mkFindTC(cuid, ivrle ? IVRLE_TS : NATIVE_LE_TS);
        for (String cuid : privateFind)
            tcs[i++] = mkFindTC(cuid, ivrle ? IVRLE_TS : DEFLATED_TS);
        for (String cuid : movecuids)
            tcs[i++] = mkRetrieveTC(cuid, ivrle ? IVRLE_TS : NATIVE_LE_TS);
        for (String cuid : getcuids)
            tcs[i++] = mkRetrieveTC(cuid, ivrle ? IVRLE_TS : NATIVE_LE_TS);
        for (TransferCapability tc : storeTransferCapability) {
            tcs[i++] = tc;
        }
        ae.setTransferCapability(tcs);
        if (!storeTransferCapability.isEmpty()) {
            ae.register(createStorageService());
        }
    }

    private TransferCapability mkRetrieveTC(String cuid, String[] ts) {
        ExtRetrieveTransferCapability tc = new ExtRetrieveTransferCapability(
                cuid, ts, TransferCapability.SCU);
        tc.setExtInfoBoolean(
                ExtRetrieveTransferCapability.RELATIONAL_RETRIEVAL, relationQR);
        if (noExtNegotiation)
            tc.setExtInfo(null);
        return tc;
    }
    
    private TransferCapability mkFindTC(String cuid, String[] ts) {
        ExtQueryTransferCapability tc = new ExtQueryTransferCapability(cuid,
                ts, TransferCapability.SCU);
        tc.setExtInfoBoolean(ExtQueryTransferCapability.RELATIONAL_QUERIES,
                relationQR);
        tc.setExtInfoBoolean(ExtQueryTransferCapability.DATE_TIME_MATCHING,
                dateTimeMatching);
        tc.setExtInfoBoolean(ExtQueryTransferCapability.FUZZY_SEMANTIC_PN_MATCHING,
                fuzzySemanticPersonNameMatching);
        if (noExtNegotiation)
            tc.setExtInfo(null);
        return tc;
    }
    
    private DicomService createStorageService() {
        String[] cuids = new String[storeTransferCapability.size()];
        int i = 0;
        for (TransferCapability tc : storeTransferCapability) {
            cuids[i++] = tc.getSopClass();
        }
        return new StorageService(cuids) {
            @Override
            protected void onCStoreRQ(Association as, int pcid, DicomObject rq,
                    PDVInputStream dataStream, String tsuid, DicomObject rsp)
                    throws IOException, DicomServiceException {
                if (storeDest == null) {
                    super.onCStoreRQ(as, pcid, rq, dataStream, tsuid, rsp);
                } else {
                    try {
                        String cuid = rq.getString(Tag.AffectedSOPClassUID);
                        String iuid = rq.getString(Tag.AffectedSOPInstanceUID);
                        BasicDicomObject fmi = new BasicDicomObject();
                        fmi.initFileMetaInformation(cuid, iuid, tsuid);
                        File file = devnull ? storeDest : new File(storeDest, iuid);
                        FileOutputStream fos = new FileOutputStream(file);
                        BufferedOutputStream bos = new BufferedOutputStream(fos,
                                fileBufferSize);
                        DicomOutputStream dos = new DicomOutputStream(bos);
                        dos.writeFileMetaInformation(fmi);
                        dataStream.copyTo(dos);
                        dos.close();
                    } catch (IOException e) {
                        throw new DicomServiceException(rq, Status.ProcessingFailure, e
                                .getMessage());
                    }
                }
            }
            
        };
    }
    
    private static String toKeyStoreType(String fname) {
        return fname.endsWith(".p12") || fname.endsWith(".P12")
                 ? "PKCS12" : "JKS";
    }
    
    private static InputStream openFileOrURL(String url) throws IOException {
        if (url.startsWith("resource:")) {
            return DcmQR.class.getClassLoader().getResourceAsStream(
                    url.substring(9));
        }
        try {
            return new URL(url).openStream();
        } catch (MalformedURLException e) {
            return new FileInputStream(url);
        }
    }
    
    private static KeyStore loadKeyStore(String url, char[] password)
    throws GeneralSecurityException, IOException
    {
        KeyStore key = KeyStore.getInstance(toKeyStoreType(url));
        InputStream in = openFileOrURL(url);
        try {
            key.load(in, password);
        } finally {
            in.close();
        }
        return key;
    }
    
    public void initTLS() throws GeneralSecurityException, IOException {
        KeyStore keyStore = loadKeyStore(keyStoreURL, keyStorePassword);
        KeyStore trustStore = loadKeyStore(trustStoreURL, trustStorePassword);
        device.initTLS(keyStore,
                keyPassword != null ? keyPassword : keyStorePassword,
                trustStore);
    }
    
    public final void setTlsWithoutEncyrption() {
        conn.setTlsWithoutEncyrption();
        remoteConn.setTlsWithoutEncyrption();
    }

    public final void setTls3DES_EDE_CBC() {
        conn.setTls3DES_EDE_CBC();
        remoteConn.setTls3DES_EDE_CBC();
    }

    public final void setTlsAES_128_CBC() {
        conn.setTlsAES_128_CBC();
        remoteConn.setTlsAES_128_CBC();
    }
}
