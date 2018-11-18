package pdg2009;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.dcm4che2.data.BasicDicomObject;
import org.dcm4che2.data.DicomElement;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.UID;
import org.dcm4che2.net.CommandUtils;
import org.dcm4che2.net.ConfigurationException;
import org.dcm4che2.net.UserIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


// prueba QRLifecycle2
//import pdg2009.QRLifecycle2.QueryRetrieveLevel;

import com.thoughtworks.xstream.XStream;

public class QR {

    private static Logger LOG = LoggerFactory.getLogger(QR.class);

    private static final int KB = 1024;


    private static final String[] DEF_TS = {
        UID.JPEGLossless,
        UID.JPEGLosslessNonHierarchical14,
        UID.JPEGLSLossless,
        UID.JPEGLSLossyNearLossless,
        UID.JPEG2000LosslessOnly,
        UID.JPEG2000,
        UID.JPEGBaseline1,
        UID.JPEGExtended24,
        UID.MPEG2,
        UID.DeflatedExplicitVRLittleEndian,
        UID.ExplicitVRBigEndian,
        UID.ExplicitVRLittleEndian,
        UID.ImplicitVRLittleEndian};

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

    private static final String[] JPLL_TS = {
        UID.JPEGLossless,
        UID.JPEGLosslessNonHierarchical14,
        UID.JPEGLSLossless,
        UID.JPEG2000LosslessOnly,
        UID.ExplicitVRLittleEndian,
        UID.ImplicitVRLittleEndian};

    private static final String[] JPLY_TS = {
        UID.JPEGBaseline1,
        UID.JPEGExtended24,
        UID.JPEGLSLossyNearLossless,
        UID.JPEG2000,
        UID.ExplicitVRLittleEndian,
        UID.ImplicitVRLittleEndian};

    private static final String[] MPEG2_TS = { UID.MPEG2 };


    private static enum TS {
        IVLE(IVRLE_TS),
        LE(NATIVE_LE_TS),
        BE(NATIVE_BE_TS),
        DEFL(DEFLATED_TS),
        JPLL(JPLL_TS),
        JPLY(JPLY_TS),
        MPEG2(MPEG2_TS),
        NOPX(NOPX_TS),
        NOPXD(NOPXDEFL_TS);

        final String[] uids;
        TS(String[] uids) { this.uids = uids; }
    }

    private static enum CUID {
        CR(UID.ComputedRadiographyImageStorage),
        CT(UID.CTImageStorage),
        MR(UID.MRImageStorage),
        US(UID.UltrasoundImageStorage),
        NM(UID.NuclearMedicineImageStorage),
        PET(UID.PositronEmissionTomographyImageStorage),
        SC(UID.SecondaryCaptureImageStorage),
        XA(UID.XRayAngiographicImageStorage),
        XRF(UID.XRayRadiofluoroscopicImageStorage),
        DX(UID.DigitalXRayImageStorageForPresentation),
        MG(UID.DigitalMammographyXRayImageStorageForPresentation),
        PR(UID.GrayscaleSoftcopyPresentationStateStorageSOPClass),
        KO(UID.KeyObjectSelectionDocumentStorage),
        SR(UID.BasicTextSRStorage);

        final String uid;
        CUID(String uid) { this.uid = uid; }

    }

    /**
     * niveles de busqueda de imagenes.
     */
    public enum Nivel { _PATIENT, _STUDY, _SERIES, _IMAGE }

    /**
     * algunas tags importantes (ya estan definidas en la clase Tag).
     */
    public static final String studyInstanceUIDTag  = "0020000d";
    public static final String seriesInstanceUIDTag = "0020000e";
    public static final String sopInstanceUIDTag    = "00080018";

    public static final String patientNameTag       = "00100010";

    //public static final Integer studyInstanceUIDTag  = Integer.parseInt("0020000D",16);
    //public static final Integer seriesInstanceUIDTag = Integer.parseInt("0020000E",16);
    //public static final Integer sopInstanceUIDTag    = Integer.parseInt("00080018",16);



    private static final String USAGE = "dcmqr <aet>[@<host>[:<port>]] [Options]";

    private static final String DESCRIPTION =
        "Query specified remote Application Entity (=Query/Retrieve SCP) "
        + "and optional (s. option -cget/-cmove) retrieve instances of "
        + "matching entities. If <port> is not specified, DICOM default port "
        + "104 is assumed. If also no <host> is specified localhost is assumed. "
        + "Also Storage Services can be provided (s. option -cstore) to receive "
        + "retrieved instances. For receiving objects retrieved by C-MOVE in a "
        + "separate association, a local listening port must be specified "
        + "(s.option -L).\n"
        + "Options:";

    private static final String EXAMPLE =
        "\nExample: dcmqr -L QRSCU:11113 QRSCP@localhost:11112 -cmove QRSCU " +
        "-qStudyDate=20060204 -qModalitiesInStudy=CT -cstore CT -cstore PR:LE " +
        "-cstoredest /tmp\n"
        + "=> Query Application Entity QRSCP listening on local port 11112 for "
        + "CT studies from Feb 4, 2006 and retrieve matching studies by C-MOVE "
        + "to own Application Entity QRSCU listing on local port 11113, "
        + "storing received CT images and Grayscale Softcopy Presentation "
        + "states to /tmp.";

    /*
    private static void exit(String msg) {
        System.err.println(msg);
        System.err.println("Try 'dcmqr -h' for more information.");
        System.exit(1);
    }
    */

    private static String[] split(String s, char delim) {
        String[] s2 = { s, null };
        int pos = s.indexOf(delim);
        if (pos != -1) {
            s2[0] = s.substring(0, pos);
            s2[1] = s.substring(pos + 1);
        }
        return s2;
    }

    private static int parseInt(String s, String errPrompt, int min, int max) {
        try {
            int i = Integer.parseInt(s);
            if (i >= min && i <= max)
                return i;
        } catch (NumberFormatException e) {
            // parameter is not a valid integer; fall through to exit
        }
        //exit(errPrompt);
        throw new RuntimeException();
    }

    private static int toPort(String port) {
        return port != null ? parseInt(port, "illegal port number", 1, 0xffff)
                : 104;
    }

    private static CommandLine parse(String[] args)
    {
        Options opts = new Options();
        OptionBuilder.withArgName("aet[@host][:port]");
        OptionBuilder.hasArg();
        OptionBuilder.withDescription("set AET, local address and listening port of local Application Entity");
        opts.addOption(OptionBuilder.create("L"));

        OptionBuilder.withArgName("username");
        OptionBuilder.hasArg();
        OptionBuilder.withDescription("enable User Identity Negotiation with specified username and optional passcode");
        opts.addOption(OptionBuilder.create("username"));

        OptionBuilder.withArgName("passcode");
        OptionBuilder.hasArg();
        OptionBuilder.withDescription("optional passcode for User Identity Negotiation, only effective with option -username");
        opts.addOption(OptionBuilder.create("passcode"));

        opts.addOption("uidnegrsp", false, "request positive User Identity Negotation response, " + "only effective with option -username");

        OptionBuilder.withArgName("NULL|3DES|AES");
        OptionBuilder.hasArg();
        OptionBuilder.withDescription("enable TLS connection without, 3DES or AES encryption");
        opts.addOption(OptionBuilder.create("tls"));

        opts.addOption("nossl2", false, "disable SSLv2Hello TLS handshake");
        opts.addOption("noclientauth", false, "disable client authentification for TLS");

        OptionBuilder.withArgName("file|url");
        OptionBuilder.hasArg();
        OptionBuilder.withDescription("file path or URL of P12 or JKS keystore, resource:tls/test_sys_1.p12 by default");
        opts.addOption(OptionBuilder.create("keystore"));

        OptionBuilder.withArgName("password");
        OptionBuilder.hasArg();
        OptionBuilder.withDescription("password for keystore file, 'secret' by default");
        opts.addOption(OptionBuilder.create("keystorepw"));

        OptionBuilder.withArgName("password");
        OptionBuilder.hasArg();
        OptionBuilder.withDescription("password for accessing the key in the keystore, keystore password by default");
        opts.addOption(OptionBuilder.create("keypw"));

        OptionBuilder.withArgName("file|url");
        OptionBuilder.hasArg();
        OptionBuilder.withDescription("file path or URL of JKS truststore, resource:tls/mesa_certs.jks by default");
        opts.addOption(OptionBuilder.create("truststore"));

        OptionBuilder.withArgName("password");
        OptionBuilder.hasArg();
        OptionBuilder.withDescription("password for truststore file, 'secret' by default");
        opts.addOption(OptionBuilder.create("truststorepw"));

        OptionBuilder.withArgName("aet");
        OptionBuilder.hasArg();
        OptionBuilder.withDescription("retrieve instances of matching entities by C-MOVE to specified destination.");
        opts.addOption(OptionBuilder.create("cmove"));

        opts.addOption("cget", false, "retrieve instances of matching entities by C-GET.");

        OptionBuilder.withArgName("cuid[:ts]");
        OptionBuilder.hasArgs();
        OptionBuilder
                .withDescription("negotiate support of specified Storage SOP Class and Transfer "
                        + "Syntaxes. The Storage SOP\nClass may be specified by its UID "
                        + "or by one\nof following key words:\n"
                        + "CR  - Computed Radiography Image Storage\n"
                        + "CT  - CT Image Storage\n"
                        + "MR  - MRImageStorage\n"
                        + "US  - Ultrasound Image Storage\n"
                        + "NM  - Nuclear Medicine Image Storage\n"
                        + "PET - PET Image Storage\n"
                        + "SC  - Secondary Capture Image Storage\n"
                        + "XA  - XRay Angiographic Image Storage\n"
                        + "XRF - XRay Radiofluoroscopic Image Storage\n"
                        + "DX  - Digital X-Ray Image Storage for Presentation\n"
                        + "                            MG  - Digital Mammography X-Ray Image Storage\n"
                        + "for Presentation\n"
                        + "PR  - Grayscale Softcopy Presentation State Storage\n"
                        + "                            KO  - Key Object Selection Document Storage\n"
                        + "SR  - Basic Text Structured Report Document Storage\n"
                        + "                            The Transfer Syntaxes may be specified by a comma\n"
                        + "                            separated list of UIDs or by one of following key\n"
                        + "                            words:\n"
                        + "                            IVRLE - offer only Implicit VR Little Endian\n"
                        + "                            Transfer Syntax\n"
                        + "                            LE - offer Explicit and Implicit VR Little Endian\n"
                        + "                            Transfer Syntax\n"
                        + "                            BE - offer Explicit VR Big Endian Transfer Syntax\n"
                        + "                            DEFL - offer Deflated Explicit VR Little\n"
                        + "                            Endian Transfer Syntax\n"
                        + "                            JPLL - offer JEPG Loss Less Transfer Syntaxes\n"
                        + "                            JPLY - offer JEPG Lossy Transfer Syntaxes\n"
                        + "                            MPEG2 - offer MPEG2 Transfer Syntax\n"
                        + "                            NOPX - offer No Pixel Data Transfer Syntax\n"
                        + "                            NOPXD - offer No Pixel Data Deflate Transfer Syntax\n"
                        + "                            If only the Storage SOP Class is specified, all\n"
                        + "                            Transfer Syntaxes listed above except No Pixel Data\n"
                        + "                            and No Pixel Data Delflate Transfer Syntax are\n"
                        + "                            offered.");
        opts.addOption(OptionBuilder.create("cstore"));

        OptionBuilder.withArgName("dir");
        OptionBuilder.hasArg();
        OptionBuilder.withDescription("store received objects into files in specified directory <dir>."
                        + " Do not store received objects\nby default.");
        opts.addOption(OptionBuilder.create("cstoredest"));

        opts.addOption("ivrle", false, "offer only Implicit VR Little Endian Transfer Syntax.");

        OptionBuilder.withArgName("maxops");
        OptionBuilder.hasArg();
        OptionBuilder.withDescription("maximum number of outstanding C-MOVE-RQ it may invoke asynchronously, 1 by default.");
        opts.addOption(OptionBuilder.create("async"));

        OptionBuilder.withArgName("maxops");
        OptionBuilder.hasArg();
        OptionBuilder.withDescription("maximum number of outstanding storage operations performed asynchronously, unlimited by\n                            default.");
        opts.addOption(OptionBuilder.create("storeasync"));

        opts.addOption("noextneg", false, "disable extended negotiation.");
        opts.addOption("rel", false, "negotiate support of relational queries and retrieval.");
        opts.addOption("datetime", false, "negotiate support of combined date and time attribute range matching.");
        opts.addOption("fuzzy", false, "negotiate support of fuzzy semantic person name attribute matching.");

        opts.addOption("retall", false, "negotiate private FIND SOP Classes to fetch all available attributes of matching entities.");
        opts.addOption(
                        "blocked",
                        false,
                        "negotiate private FIND SOP Classes "
                                + "to return attributes of several matching entities per FIND\n"
                                + "                            response.");
        opts.addOption("vmf", false, "negotiate private FIND SOP Classes to "
                + "return attributes of legacy CT/MR images of one series as\n"
                + "                           virtual multiframe object.");
        opts.addOption("pdv1", false,
                "send only one PDV in one P-Data-TF PDU, pack command and data "
                        + "PDV in one P-DATA-TF PDU\n"
                        + "                           by default.");
        opts.addOption("tcpdelay", false,
                "set TCP_NODELAY socket option to false, true by default");

        OptionBuilder.withArgName("ms");
        OptionBuilder.hasArg();
        OptionBuilder
                .withDescription("timeout in ms for TCP connect, no timeout by default");
        opts.addOption(OptionBuilder.create("connectTO"));

        OptionBuilder.withArgName("ms");
        OptionBuilder.hasArg();
        OptionBuilder
                .withDescription("delay in ms for Socket close after sending A-ABORT, 50ms by default");
        opts.addOption(OptionBuilder.create("soclosedelay"));

        OptionBuilder.withArgName("ms");
        OptionBuilder.hasArg();
        OptionBuilder
                .withDescription("period in ms to check for outstanding DIMSE-RSP, 10s by default");
        opts.addOption(OptionBuilder.create("reaper"));

        OptionBuilder.withArgName("ms");
        OptionBuilder.hasArg();
        OptionBuilder.withDescription("timeout in ms for receiving C-FIND-RSP, 60s by default");
        opts.addOption(OptionBuilder.create("cfindrspTO"));

        OptionBuilder.withArgName("ms");
        OptionBuilder.hasArg();
        OptionBuilder
                .withDescription("timeout in ms for receiving C-MOVE-RSP and C-GET RSP, 600s by default");
        opts.addOption(OptionBuilder.create("cmoverspTO"));

        OptionBuilder.withArgName("ms");
        OptionBuilder.hasArg();
        OptionBuilder
                .withDescription("timeout in ms for receiving C-GET-RSP and C-MOVE RSP, 600s by default");
        opts.addOption(OptionBuilder.create("cgetrspTO"));

        OptionBuilder.withArgName("ms");
        OptionBuilder.hasArg();
        OptionBuilder
                .withDescription("timeout in ms for receiving A-ASSOCIATE-AC, 5s by default");
        opts.addOption(OptionBuilder.create("acceptTO"));

        OptionBuilder.withArgName("ms");
        OptionBuilder.hasArg();
        OptionBuilder
                .withDescription("timeout in ms for receiving A-RELEASE-RP, 5s by default");
        opts.addOption(OptionBuilder.create("releaseTO"));

        OptionBuilder.withArgName("KB");
        OptionBuilder.hasArg();
        OptionBuilder
                .withDescription("maximal length in KB of received P-DATA-TF PDUs, 16KB by default");
        opts.addOption(OptionBuilder.create("rcvpdulen"));

        OptionBuilder.withArgName("KB");
        OptionBuilder.hasArg();
        OptionBuilder
                .withDescription("maximal length in KB of sent P-DATA-TF PDUs, 16KB by default");
        opts.addOption(OptionBuilder.create("sndpdulen"));

        OptionBuilder.withArgName("KB");
        OptionBuilder.hasArg();
        OptionBuilder
                .withDescription("set SO_RCVBUF socket option to specified value in KB");
        opts.addOption(OptionBuilder.create("sorcvbuf"));

        OptionBuilder.withArgName("KB");
        OptionBuilder.hasArg();
        OptionBuilder.withDescription("set SO_SNDBUF socket option to specified value in KB");
        opts.addOption(OptionBuilder.create("sosndbuf"));

        OptionBuilder.withArgName("KB");
        OptionBuilder.hasArg();
        OptionBuilder.withDescription("minimal buffer size to write received object to file, 1KB by default");
        opts.addOption(OptionBuilder.create("filebuf"));

        OptionGroup qrlevel = new OptionGroup();

        OptionBuilder.withDescription("perform patient level query, multiple "
                + "exclusive with -S and -I, perform study level query\n"
                + "                            by default.");
        OptionBuilder.withLongOpt("patient");
        opts.addOption(OptionBuilder.create("P"));

        OptionBuilder.withDescription("perform series level query, multiple "
                + "exclusive with -P and -I, perform study level query\n"
                + "                            by default.");
        OptionBuilder.withLongOpt("series");
        opts.addOption(OptionBuilder.create("S"));

        OptionBuilder.withDescription("perform instance level query, multiple "
                + "exclusive with -P and -S, perform study level query\n"
                + "                            by default.");
        OptionBuilder.withLongOpt("image");
        opts.addOption(OptionBuilder.create("I"));

        opts.addOptionGroup(qrlevel);

        OptionBuilder.withArgName("[seq/]attr=value");
        OptionBuilder.hasArgs();
        OptionBuilder.withValueSeparator('=');
        OptionBuilder
                .withDescription("specify matching key. attr can be "
                        + "specified by name or tag value (in hex), e.g. PatientName\n"
                        + "or 00100010. Attributes in nested Datasets can\n"
                        + "be specified by including the name/tag value of\n"
                        + "                            the sequence attribute, e.g. 00400275/00400009\n"
                        + "for Scheduled Procedure Step ID in the Request\n"
                        + "Attributes Sequence");
        opts.addOption(OptionBuilder.create("q"));

        OptionBuilder.withArgName("attr");
        OptionBuilder.hasArgs();
        OptionBuilder
                .withDescription("specify additional return key. attr can "
                        + "be specified by name or tag value (in hex).");
        opts.addOption(OptionBuilder.create("r"));

        OptionBuilder.withArgName("num");
        OptionBuilder.hasArg();
        OptionBuilder
                .withDescription("cancel query after receive of specified "
                        + "number of responses, no cancel by default");
        opts.addOption(OptionBuilder.create("C"));

        OptionBuilder.withArgName("aet");
        OptionBuilder.hasArg();
        OptionBuilder.withDescription("retrieve matching objects to specified "
                + "move destination.");
        opts.addOption(OptionBuilder.create("cmove"));

        opts.addOption("evalRetrieveAET", false,
                "Only Move studies not allready stored on destination AET");
        opts
                .addOption("lowprior", false,
                        "LOW priority of the C-FIND/C-MOVE operation, MEDIUM by default");
        opts
                .addOption("highprior", false,
                        "HIGH priority of the C-FIND/C-MOVE operation, MEDIUM by default");

        OptionBuilder.withArgName("num");
        OptionBuilder.hasArg();
        OptionBuilder.withDescription("repeat query (and retrieve) several times");
        opts.addOption(OptionBuilder.create("repeat"));

        OptionBuilder.withArgName("ms");
        OptionBuilder.hasArg();
        OptionBuilder.withDescription("delay in ms between repeated query (and retrieve), no delay by default");
        opts.addOption(OptionBuilder.create("repeatdelay"));

        opts.addOption("reuseassoc", false, "Reuse association for repeated query (and retrieve)");
        opts.addOption("closeassoc", false, "Close association between repeated query (and retrieve)");

        opts.addOption("h", "help", false, "print this message");
        opts.addOption("V", "version", false, "print the version information and exit");
        CommandLine cl = null;
        try
        {
            cl = new GnuParser().parse(opts, args);
        }
        catch (ParseException e)
        {
            //exit("dcmqr: " + e.getMessage());
            throw new RuntimeException("unreachable");
        }
        if (cl.hasOption('V'))
        {
            Package p = QRLifecycle2.class.getPackage();
            System.out.println("dcmqr v" + p.getImplementationVersion());
            //System.exit(0);
        }
        if (cl.hasOption('h') || cl.getArgList().size() != 1)
        {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(USAGE, DESCRIPTION, opts, EXAMPLE);
            //System.exit(0);
        }

        return cl;
    }

    public static CommandLine make_patient_studies_query(
            String localAE, Integer localPort,
            String remoteAE, String remoteIP, Integer remotePort,
            String name1, String lastname1,
            String patientId,
            //Date studyDateStart, Date studyDateEnd )
            Integer fromY, Integer fromM, Integer fromD, // from date
            Integer toY, Integer toM, Integer toD ) // to date
    {
        // Query keys:
        // - PatientName
        // - PatientID
        // - StudyDate
        // - level=PATIENT

        // Retrieve keys:
        // - StudyID
        // - StudyUID
        // - SeriesInStudyCount
        // - Patient Birthdate
        // - Patient sex

        List<String> additionalReturnKeys = new ArrayList<String>();
        additionalReturnKeys.add( Integer.toHexString( Tag.StudyID ) ); // no necesario, no viene
        additionalReturnKeys.add( Integer.toHexString( Tag.StudyInstanceUID ) ); // no necesario, no viene
        //additionalReturnKeys.add( Integer.toHexString( Tag.SeriesNumber ) );
        additionalReturnKeys.add( Integer.toHexString( Tag.NumberOfPatientRelatedStudies ) );   // # estudios
        additionalReturnKeys.add( Integer.toHexString( Tag.NumberOfPatientRelatedSeries ) );    // # series
        additionalReturnKeys.add( Integer.toHexString( Tag.NumberOfPatientRelatedInstances ) ); // # imagenes
        additionalReturnKeys.add( Integer.toHexString( Tag.PatientBirthTime ) );  // no necesario, no viene (en el maciel si viene)
        additionalReturnKeys.add( Integer.toHexString( Tag.PatientSex ) );        // no necesario, no viene (en el maciel si viene)
        //additionalReturnKeys.add( Integer.toHexString( Tag.ModalitiesInStudy ) ); // no necesario, no viene
        //additionalReturnKeys.add( Integer.toHexString( Tag.Modality ) );          // no necesario, no viene
        //additionalReturnKeys.add( Integer.toHexString( Tag.PersonName ) );        // no necesario, no viene


        // Armo localHost y remoteHost --------------------------------------------
        //
        String localHost = localAE +":"+localPort; // QRSCUCHE:44445
        String remoteHost = remoteAE + "@" + remoteIP + ":" + remotePort; // SERVERAE@127.0.0.1:33333

        // Armo el nivel ----------------------------------------------------------
        // Tambien necesito verificar que las tags de UIDs que quiero pedir, estan. Estas dependen del nivel.
        //
        String qNivel = "-P";

        // Armo studyDate ---------------------------------------------------------
        //
        // FIXME: arma las fechas como datatypes, pero si hago un print de la fecha es del anio 109 en lugar de 2009.
        String qStudyDate = "";
        if ( fromY != null && fromM != null && fromD != null )
        {
            String mes = ((fromM<10) ? ("0"+fromM) : (""+fromM) );
            String dia = ((fromD<10) ? ("0"+fromD) : (""+fromD) );
            qStudyDate = "-qStudyDate="+fromY + mes + dia;

            // ClearCanvas no banca hacer request con rango de fecha a nivel de Image.
            if ( toY != null && toM != null && toD != null )
            {
                mes = ((toM<10) ? ("0"+toM) : (""+toM));
                dia = ((toD<10)  ? ("0"+toD)  : (""+toD) );
                qStudyDate += "-" + toY + mes + dia;
            }
        }

        // apellido1 apellido2^nombre1 nombre2 -------------------------------------
        // Unico string con datatype PN (ver parte 5 de la norma dicom)
        //
        String qPatientName = "*";
        if (lastname1 != null) // Si quiere buscar por nombre, tiene que poner el apellido por lo menos.
        {
            qPatientName = lastname1 + qPatientName;

            if (name1 != null)
                qPatientName += "^" + name1;
        }
        else if (name1 != null) // si no tengo apellido, busco solo por nombre
        {
            qPatientName += "^" + name1;
        }

        // Armo modalities in study
        // TODO: como hago con varias modalities? NO SE PUEDE.
//        String qModalitiesInStudy = "-qModalitiesInStudy="+modalities.get(0);

        // Armo los argumentos
        ArrayList<String> qargs = new ArrayList<String>();
        qargs.add("-L");
        qargs.add(localHost);
        qargs.add(remoteHost);

        // Si pide buscar por nombres
        if (qPatientName.compareTo("*") != 0) qargs.add("-qPatientName="+qPatientName);

        if (qStudyDate.compareTo("") != 0) qargs.add(qStudyDate);

        if (patientId != null) qargs.add("-qPatientID="+patientId);


//        qargs.add(qModalitiesInStudy);

        // -r
        for ( String key : additionalReturnKeys )
        {
            qargs.add("-r");
            qargs.add(key);
        }

        // -P, -S, -I
        qargs.add(qNivel);

        System.out.println("========================================");
        System.out.println("Argumentos consulta:");
        System.out.println(qargs);
        System.out.println("========================================");

        String[] argus = new String[ qargs.size() ];

        //qargs.toArray( argus );
        for (int i=0; i<qargs.size(); i++)
        {
            //System.out.println( qargs.get(i) );
            argus[i] = qargs.get(i);
        }


        CommandLine cm = parse(argus);

        System.out.println("========================================");
        System.out.println("Command Line:");
        System.out.println( cm.getArgList() );
        Option[] options = cm.getOptions();
        for (int i=0; i<options.length; i++)
        {
            System.out.println( options[i].getDescription() +" "+
                                options[i].getArgName() +" "+
                                options[i].getValuesList() );
        }
        System.out.println("========================================");

        return cm;

    } // make_patient_studies_query

    public static CommandLine make_studies_query(
            String localAE, Integer localPort,
            String remoteAE, String remoteIP, Integer remotePort,
            String patientId, String studyId,
            String patientName, String patientLastname,
            Integer fromY, Integer fromM, Integer fromD, // from date
            Integer toY, Integer toM, Integer toD ) // to date)
    {
      // Query keys:
      // - PatientName
      // - PatientID
      // - StudyDate
      // - level=PATIENT

      // Retrieve keys:
      // - StudyID
      // - StudyUID
      // - SeriesInStudyCount
      // - Patient Birthdate
      // - Patient sex

      ArrayList<String> qargs = new ArrayList<String>(); // query
      List<String> additionalReturnKeys = new ArrayList<String>(); // retrieve

      // para no sobreescribir key
      if (studyId == null)
         additionalReturnKeys.add( Integer.toHexString( Tag.StudyID ) );

      if (patientName == null)
         additionalReturnKeys.add( Integer.toHexString( Tag.PatientName ) );
      else
      {
         String pnameSearch = "";
         if (patientLastname != null)
         {
            pnameSearch = patientLastname + "*^" + patientName + "*";
         }
         else
         {
            pnameSearch = "*^" + patientName + "*";
         }

         qargs.add("-qPatientName="+pnameSearch);
      }

      additionalReturnKeys.add( Integer.toHexString( Tag.StudyInstanceUID ) );
      additionalReturnKeys.add( Integer.toHexString( Tag.StudyDescription ) );

      //additionalReturnKeys.add( Integer.toHexString( Tag.SeriesNumber ) );
      additionalReturnKeys.add( Integer.toHexString( Tag.NumberOfPatientRelatedStudies ) );   // # estudios
      additionalReturnKeys.add( Integer.toHexString( Tag.NumberOfPatientRelatedSeries ) );    // # series
      additionalReturnKeys.add( Integer.toHexString( Tag.NumberOfPatientRelatedInstances ) ); // # imagenes

      if ( patientId == null )
          additionalReturnKeys.add( Integer.toHexString( Tag.PatientID ) ); // estoy consultando por este arg, solo puedo pedirlo si el que viene es null


      additionalReturnKeys.add( Integer.toHexString( Tag.PatientBirthTime ) );
      additionalReturnKeys.add( Integer.toHexString( Tag.PatientSex ) );
      additionalReturnKeys.add( Integer.toHexString( Tag.ModalitiesInStudy ) );
      //additionalReturnKeys.add( Integer.toHexString( Tag.Modality ) ); // se usa para series


      // Armo localHost y remoteHost --------------------------------------------
      //
      String localHost = localAE +":"+localPort; // QRSCUCHE:44445
      String remoteHost = remoteAE + "@" + remoteIP + ":" + remotePort; // SERVERAE@127.0.0.1:33333

      // Armo el nivel ----------------------------------------------------------
      // Tambien necesito verificar que las tags de UIDs que quiero pedir, estan. Estas dependen del nivel.
      //
      // Nivel por defecto> study

      // Armo studyDate ---------------------------------------------------------
      //
      String qStudyDate = "";
      if ( fromY != null && fromM != null && fromD != null)
      {
         String mes = ((fromM<10) ? ("0"+fromM) : (""+fromM));
         String dia = ((fromD<10) ? ("0"+fromD) : (""+fromD) );
         qStudyDate = "-qStudyDate="+fromY + mes + dia;

         // ClearCanvas no banca hacer request con rango de fecha a nivel de Image.
         if ( toY != null && toM != null && toD != null)
         {
            mes = ((toM<10) ? ("0"+toM) : (""+toM));
            dia = ((toD<10)  ? ("0"+toD)  : (""+toD) );
            qStudyDate += "-" + toY + mes + dia;
         }
      }

      // Armo modalities in study
      // TODO: como hago con varias modalities? NO SE PUEDE.
      //String qModalitiesInStudy = "-qModalitiesInStudy="+modalities.get(0);

      // Armo los argumentos

      qargs.add("-L");
      qargs.add(localHost);
      qargs.add(remoteHost);

      if (qStudyDate.compareTo("") != 0) qargs.add(qStudyDate);

      if (patientId != null) qargs.add("-qPatientID="+patientId);

      if (studyId != null) qargs.add("-qStudyID="+studyId);

      // Retrieve keys
      for ( String key : additionalReturnKeys )
      {
         qargs.add("-r");
         qargs.add(key);
      }

      System.out.println("========================================");
      System.out.println("Argumentos consulta:");
      System.out.println(qargs);
      System.out.println("========================================");

      String[] argus = new String[ qargs.size() ];

      //qargs.toArray( argus );
      for (int i=0; i<qargs.size(); i++)
      {
         //System.out.println( qargs.get(i) );
         argus[i] = qargs.get(i);
      }

      CommandLine cm = parse(argus);

      System.out.println("========================================");
      System.out.println("Command Line:");
      System.out.println( cm.getArgList() );
      Option[] options = cm.getOptions();
      for (int i=0; i<options.length; i++)
      {
         System.out.println( options[i].getDescription() +" "+
                             options[i].getArgName() +" "+
                             options[i].getValuesList() );
      }
      System.out.println("========================================");

      return cm;

    } // make_studies_query



    public static CommandLine make_study_series_query(
            String localAE, Integer localPort,
            String remoteAE, String remoteIP, Integer remotePort,
            String studyId, String studyUID,
            String studyDate,
            String modality )
    {
        // Query keys:
        // - StudyID
        // - StudyUID
        // - ModalitiesInStudy
        // - level=SERIES

        // Retrieve keys:
        // - ImageUID // YA OBTENGO el UID, no necesito ir mas abajo...
        // - ImageSize ??
        // - ImagesInSeriesCount
        // - ...


        List<String> additionalReturnKeys = new ArrayList<String>();
        //additionalReturnKeys.add( Integer.toHexString( Tag.StudyID ) ); // no tira nada
        if ( studyUID == null )
            additionalReturnKeys.add( Integer.toHexString( Tag.StudyInstanceUID ) ); // es tambien parametro de busqueda

//        additionalReturnKeys.add( Integer.toHexString( Tag.SeriesInstanceUID ) );
//        additionalReturnKeys.add( Integer.toHexString( Tag.SeriesNumber ) );
        //additionalReturnKeys.add( Integer.toHexString( Tag.NumberOfPatientRelatedStudies ) );   // # estudios // no tira nada
        //additionalReturnKeys.add( Integer.toHexString( Tag.NumberOfPatientRelatedSeries ) );    // # series // no tira nada
        //additionalReturnKeys.add( Integer.toHexString( Tag.NumberOfPatientRelatedInstances ) ); // # imagenes // no tira nada
        //additionalReturnKeys.add( Integer.toHexString( Tag.NumberOfStudyRelatedSeries ) );    // # series en estudio // no tira nada
        //additionalReturnKeys.add( Integer.toHexString( Tag.NumberOfStudyRelatedInstances ) ); // # imagenes en estudio // no tira nada
        additionalReturnKeys.add( Integer.toHexString( Tag.NumberOfSeriesRelatedInstances ) );

        additionalReturnKeys.add( Integer.toHexString( Tag.RequestAttributesSequence ) );
        additionalReturnKeys.add( Integer.toHexString( Tag.SeriesDescription ) );

        if (modality == null)
            additionalReturnKeys.add( Integer.toHexString( Tag.Modality ) ); // es tambien parametro de busqueda

        // Armo localHost y remoteHost --------------------------------------------
        //
        String localHost = localAE +":"+localPort; // QRSCUCHE:44445
        String remoteHost = remoteAE + "@" + remoteIP + ":" + remotePort; // SERVERAE@127.0.0.1:33333

        // Nivel de Series ----------------------------------------------------------
        //
        String qNivel = "-S";


        // Armo modalities in study
        // TODO: como hago con varias modalities? NO SE PUEDE.
//        String qModalitiesInStudy = "-qModalitiesInStudy="+modalities.get(0);

        // Armo los argumentos
        ArrayList<String> qargs = new ArrayList<String>();
        qargs.add("-L");
        qargs.add(localHost);
        qargs.add(remoteHost);

//        if (modality != null) qargs.add("-qModality="+modality);
        if (studyId  != null) qargs.add("-qStudyID="+studyId);
        if (studyUID != null) qargs.add("-qStudyInstanceUID="+studyUID);
        if (studyDate != null) qargs.add("-qStudyDate="+studyDate);

//        qargs.add(qModalitiesInStudy);

        // -r
        for ( String key : additionalReturnKeys )
        {
            qargs.add("-r");
            qargs.add(key);
        }

        // -P, -S, -I
        qargs.add(qNivel);


        System.out.println("========================================");
        System.out.println("Argumentos consulta:");
        System.out.println(qargs);
        System.out.println("========================================");


        String[] argus = new String[ qargs.size() ];

        //qargs.toArray( argus );
        for (int i=0; i<qargs.size(); i++)
        {
            System.out.println( qargs.get(i) );
            argus[i] = qargs.get(i);
        }


        CommandLine cm = parse(argus);

        System.out.println("========================================");
        System.out.println( cm.getArgList() );
        Option[] options = cm.getOptions();
        for (int i=0; i<options.length; i++)
        {
            System.out.println(  options[i].getArgName() +" "+ options[i].getValuesList() );
        }
        System.out.println("========================================");

        return cm;
    }


    public static CommandLine make_serie_images_query(
            String localAE, Integer localPort,
            String remoteAE, String remoteIP, Integer remotePort,
            String studyUID, // Se agrega para consulta en COMEPA, MACIEL y ClearCanvas Server andan sin este parametro.
            String serieUID, String serieID )
    {
        // Query keys:
        // - SerieUID
        // - StudyUID
        // - level=IMAGE

        // Retrieve keys:
        // - ya pedi el UID en la consulta de las series
        //   puedo pedir alguna informacion mas de las imagenes de la serie?
        // - ...


        List<String> additionalReturnKeys = new ArrayList<String>();
        //additionalReturnKeys.add( Integer.toHexString( Tag.StudyID ) ); // no tira nada
        //additionalReturnKeys.add( Integer.toHexString( Tag.StudyInstanceUID ) ); // SACO PORQUE AHORA VIENE COMO PARAMETRO
     //   additionalReturnKeys.add( Integer.toHexString( Tag.SeriesInstanceUID ) );

        // TODO: instance uid???
        // TODO: probar esta en el maciel
        //additionalReturnKeys.add( Integer.toHexString( Tag.SOPInstanceUID ) ); // no tira nada

        // TODO: Ver si con alguna de estas funca>
        // Tag.RequestedSOPInstanceUID
        // ReferencedSOPInstanceUID
        // SeriesInstanceUID
        // StudyInstanceUID

        additionalReturnKeys.add( Integer.toHexString( Tag.InstanceNumber) );
        additionalReturnKeys.add( Integer.toHexString( Tag.SOPClassUID ) );
        additionalReturnKeys.add( Integer.toHexString( Tag.SOPInstanceUID ) );

     //   additionalReturnKeys.add( Integer.toHexString( Tag.SeriesNumber ) );
        //additionalReturnKeys.add( Integer.toHexString( Tag.NumberOfPatientRelatedStudies ) );   // # estudios // no tira nada
        //additionalReturnKeys.add( Integer.toHexString( Tag.NumberOfPatientRelatedSeries ) );    // # series // no tira nada
        //additionalReturnKeys.add( Integer.toHexString( Tag.NumberOfPatientRelatedInstances ) ); // # imagenes // no tira nada

        //additionalReturnKeys.add( Integer.toHexString( Tag.NumberOfStudyRelatedSeries ) );    // # series en estudio // no tira nada
        //additionalReturnKeys.add( Integer.toHexString( Tag.NumberOfStudyRelatedInstances ) ); // # imagenes en estudio // no tira nada
        additionalReturnKeys.add( Integer.toHexString( Tag.NumberOfSeriesRelatedInstances ) );

        // Sirve para imagenes multiframe
        additionalReturnKeys.add( Integer.toHexString( Tag.NumberOfFrames ) );


        // Armo localHost y remoteHost --------------------------------------------
        //
        String localHost = localAE +":"+localPort; // QRSCUCHE:44445
        String remoteHost = remoteAE + "@" + remoteIP + ":" + remotePort; // SERVERAE@127.0.0.1:33333

        // Nivel de Series ----------------------------------------------------------
        //
        String qNivel = "-I";

        // Armo los argumentos -------------------------
        //
        ArrayList<String> qargs = new ArrayList<String>();
        qargs.add("-L");
        qargs.add(localHost);
        qargs.add(remoteHost);
        if (studyUID != null) qargs.add("-qStudyInstanceUID="+studyUID);
        if (serieUID != null) qargs.add("-qSeriesInstanceUID="+serieUID);
        if (serieID != null) qargs.add("-qSeriesNumber="+serieID);


        // prueba COMEPA no funciona
        //qargs.add("-qSOPInstanceUID=1.2.840.113564.172282448.2009081023193035973.2003000225000");

        // prueba COMEPA no funciona
        //qargs.add("-qInstanceNumber=109855");

        // Si pide buscar por nombres ------------------------
        //
        //if (qStudyId.compareTo("") != 0) qargs.add(qStudyId);


        // -r
        for ( String key : additionalReturnKeys )
        {
            qargs.add("-r");
            qargs.add(key);
        }

        // -P, -S, -I
        qargs.add(qNivel);


        System.out.println("========================================");
        System.out.println("Argumentos consulta:");
        System.out.println(qargs);
        System.out.println("========================================");


        String[] argus = new String[ qargs.size() ];

        //qargs.toArray( argus );
        for (int i=0; i<qargs.size(); i++)
        {
            System.out.println( qargs.get(i) );
            argus[i] = qargs.get(i);
        }


        CommandLine cm = parse(argus);

        System.out.println("========================================");
        System.out.println( cm.getArgList() );
        Option[] options = cm.getOptions();
        for (int i=0; i<options.length; i++)
        {
            System.out.println(  options[i].getArgName() +" "+ options[i].getValuesList() );
        }
        System.out.println("========================================");

        return cm;
    }




    /**
     *
     * Si quiere buscar por nombre, tiene que poner el apellido por lo menos.
     *
     * @param nivel P=patient, S=series, I=images. Si es NULL, busca por nivel de study.
     * @return
     */
    @SuppressWarnings("deprecation")
    public static CommandLine make_query_retrieve(
            String localAE, Integer localPort,
            String remoteAE, String remoteIP, Integer remotePort,
            String name1, String lastname1,
            Date studyDate,
            Date studyDateEnd,
            ArrayList<String> modalities,
            ArrayList<String> additionalReturnKeys,
            Nivel nivel )
    {
        // Armo el nivel
        // Tambien necesito verificar que las tags de UIDs que quiero pedir, estan. Estas dependen del nivel.
        String qNivel = ""; // Por defecto a nivel del estudio
        switch ( nivel )
        {
            case _PATIENT:
                qNivel = "-P";
            break;
            case _SERIES:
                qNivel = "-S";
                if (additionalReturnKeys == null) additionalReturnKeys = new ArrayList<String>();
                if ( !additionalReturnKeys.contains(seriesInstanceUIDTag) )
                {
                    additionalReturnKeys.add(seriesInstanceUIDTag);
                }
            break;
            case _IMAGE:
                qNivel = "-I";
                if (additionalReturnKeys == null) additionalReturnKeys = new ArrayList<String>();
                if ( !additionalReturnKeys.contains(seriesInstanceUIDTag) )
                {
                    additionalReturnKeys.add(seriesInstanceUIDTag);
                }
                if ( !additionalReturnKeys.contains(sopInstanceUIDTag) )
                {
                    additionalReturnKeys.add(sopInstanceUIDTag);
                }
            break;
        }

        // Armo localHost y remoteHost
        String localHost = localAE +":"+localPort; // QRSCUCHE:44445
        String remoteHost = remoteAE + "@" + remoteIP + ":" + remotePort; // SERVERAE@127.0.0.1:33333

        // Armo studyDate
        // TODO: como hago para pedir con un rango de fechas.
        String qStudyDate = "";
        if ( studyDate != null )
        {
            String mes = ( (studyDate.getMonth()<10) ? ("0"+studyDate.getMonth()) : (""+studyDate.getMonth()) );
            String dia = ( (studyDate.getDate()<10)  ? ("0"+studyDate.getDate())  : (""+studyDate.getDate()) );
            qStudyDate = "-qStudyDate="+studyDate.getYear() + mes + dia;

            // ClearCanvas no banca hacer request con rango de fecha a nivel de Image.
            if ( studyDateEnd != null && nivel != Nivel._IMAGE )
            {
                mes = ( (studyDateEnd.getMonth()<10) ? ("0"+studyDateEnd.getMonth()) : (""+studyDateEnd.getMonth()) );
                dia = ( (studyDateEnd.getDate()<10)  ? ("0"+studyDateEnd.getDate())  : (""+studyDateEnd.getDate()) );
                qStudyDate += "-" + studyDateEnd.getYear() + mes + dia;
            }
        }


//        System.out.println( "qStudyDate: " + qStudyDate );

        // apellido1 apellido2^nombre1 nombre2
        // Unico string con datatype PN (ver parte 5 de la norma dicom)
        String qPatientName = "*";
        if (lastname1 != null) // Si quiere buscar por nombre, tiene que poner el apellido por lo menos.
        {
            qPatientName = lastname1 + qPatientName;

            if (name1 != null)
                qPatientName += "^" + name1;

        }

        // Armo modalities in study
        // TODO: como hago con varias modalities? NO SE PUEDE.
        String qModalitiesInStudy = "-qModalitiesInStudy="+modalities.get(0);

        // Armo los argumentos
        ArrayList<String> qargs = new ArrayList<String>();
        qargs.add("-L");
        qargs.add(localHost);
        qargs.add(remoteHost);

        // Si pide buscar por nombres
        if (qPatientName.compareTo("*") != 0) qargs.add("-qPatientName="+qPatientName);

        if (qStudyDate.compareTo("") != 0) qargs.add(qStudyDate);

        qargs.add(qModalitiesInStudy);

        // -r
        for ( String key : additionalReturnKeys )
        {
            qargs.add("-r");
            qargs.add(key);
        }

        // -P, -S, -I
        qargs.add(qNivel);


        String[] argus = new String[ qargs.size() ];

        //qargs.toArray( argus );
        for (int i=0; i<qargs.size(); i++)
        {
            System.out.println( qargs.get(i) );
            argus[i] = qargs.get(i);
        }


        CommandLine cm = parse(argus);

        System.out.println("========================================");
        System.out.println( cm.getArgList() );
        Option[] options = cm.getOptions();
        for (int i=0; i<options.length; i++)
        {
            System.out.println(  options[i].getArgName() +" "+ options[i].getValuesList() );
        }
        System.out.println("========================================");

        // Lo que queda es igual a esto que funciona???
//        String[] args2 = { "-L", "QRSCUCHE:44445", "SERVERAE@127.0.0.1:33333",
//                "-qStudyDate=20000105-20020105", "-qModalitiesInStudy=CT",
//                "-r", "0020000e", "-r", "00080018", "-S" };
//
//        if ( args2.length != argus.length )
//            System.out.println("=== No tienen el mismo tamanio ===");
//
//        for (int j=0; j<args2.length; j++)
//        {
//            if ( args2[j].compareTo( argus[j] ) != 0 )
//                System.out.println( j + ": " + args2[j] + " != " + argus[j] );
//            else
//                System.out.println( j + ": " + args2[j] + " == " + argus[j] );
//        }
//

        /*

        cm = parse(args2);

        System.out.println("========================================");
        System.out.println( cm.getArgList() );
        options = cm.getOptions();
        for (int i=0; i<options.length; i++)
        {
            System.out.println(  options[i].getArgName() +" "+ options[i].getValuesList() );
        }
        System.out.println("========================================");
        */


//      System.out.println( qargs );

        return cm;
    }

    @SuppressWarnings("unchecked")
    public static List<DicomObject> send_query( CommandLine cl ) throws Exception
    {
        List<DicomObject> result = null;

        //QRLifecycle dcmqr = new QRLifecycle();
        QRLifecycle2 dcmqr = new QRLifecycle2(); // Copia del de DCM4CHE

        final List<String> argList = cl.getArgList();
        String remoteAE = argList.get(0);

        String[] calledAETAddress = split(remoteAE, '@');
        dcmqr.setCalledAET(calledAETAddress[0], cl.hasOption("reuseassoc"));

        if (calledAETAddress[1] == null) {
            dcmqr.setRemoteHost("127.0.0.1");
            dcmqr.setRemotePort(104);
        } else {
            String[] hostPort = split(calledAETAddress[1], ':');
            dcmqr.setRemoteHost(hostPort[0]);
            dcmqr.setRemotePort(toPort(hostPort[1]));
        }

        if (cl.hasOption("L"))
        {
            String localAE = cl.getOptionValue("L");
            String[] localPort = split(localAE, ':');
            if (localPort[1] != null) {
                dcmqr.setLocalPort(toPort(localPort[1]));
            }
            String[] callingAETHost = split(localPort[0], '@');
            dcmqr.setCalling(callingAETHost[0]);
            if (callingAETHost[1] != null) {
                dcmqr.setLocalHost(callingAETHost[1]);
            }
        }

        if (cl.hasOption("username")) {
            String username = cl.getOptionValue("username");
            UserIdentity userId;
            if (cl.hasOption("passcode")) {
                String passcode = cl.getOptionValue("passcode");
                userId = new UserIdentity.UsernamePasscode(username,
                        passcode.toCharArray());
            } else {
                userId = new UserIdentity.Username(username);
            }
            userId.setPositiveResponseRequested(cl.hasOption("uidnegrsp"));
            dcmqr.setUserIdentity(userId);
        }

        if (cl.hasOption("connectTO"))
            dcmqr.setConnectTimeout(parseInt(cl.getOptionValue("connectTO"),
                    "illegal argument of option -connectTO", 1, Integer.MAX_VALUE));

        if (cl.hasOption("reaper"))
            dcmqr.setAssociationReaperPeriod(parseInt(cl.getOptionValue("reaper"),
                            "illegal argument of option -reaper", 1, Integer.MAX_VALUE));

        if (cl.hasOption("cfindrspTO"))
            dcmqr.setDimseRspTimeout(parseInt(cl.getOptionValue("cfindrspTO"),
                    "illegal argument of option -cfindrspTO", 1, Integer.MAX_VALUE));

        if (cl.hasOption("cmoverspTO"))
            dcmqr.setRetrieveRspTimeout(parseInt(cl.getOptionValue("cmoverspTO"),
                    "illegal argument of option -cmoverspTO", 1, Integer.MAX_VALUE));

        if (cl.hasOption("cgetrspTO"))
            dcmqr.setRetrieveRspTimeout(parseInt(cl.getOptionValue("cgetrspTO"),
                    "illegal argument of option -cgetrspTO", 1, Integer.MAX_VALUE));

        if (cl.hasOption("acceptTO"))
            dcmqr.setAcceptTimeout(parseInt(cl.getOptionValue("acceptTO"),
                    "illegal argument of option -acceptTO", 1, Integer.MAX_VALUE));

        if (cl.hasOption("releaseTO"))
            dcmqr.setReleaseTimeout(parseInt(cl.getOptionValue("releaseTO"),
                    "illegal argument of option -releaseTO", 1, Integer.MAX_VALUE));

        if (cl.hasOption("soclosedelay"))
            dcmqr.setSocketCloseDelay(parseInt(cl.getOptionValue("soclosedelay"),
                    "illegal argument of option -soclosedelay", 1, 10000));

        if (cl.hasOption("rcvpdulen"))
            dcmqr.setMaxPDULengthReceive(parseInt(cl.getOptionValue("rcvpdulen"),
                    "illegal argument of option -rcvpdulen", 1, 10000) * KB);

        if (cl.hasOption("sndpdulen"))
            dcmqr.setMaxPDULengthSend(parseInt(cl.getOptionValue("sndpdulen"),
                    "illegal argument of option -sndpdulen", 1, 10000) * KB);

        if (cl.hasOption("sosndbuf"))
            dcmqr.setSendBufferSize(parseInt(cl.getOptionValue("sosndbuf"),
                    "illegal argument of option -sosndbuf", 1, 10000) * KB);

        if (cl.hasOption("sorcvbuf"))
            dcmqr.setReceiveBufferSize(parseInt(cl.getOptionValue("sorcvbuf"),
                    "illegal argument of option -sorcvbuf", 1, 10000) * KB);

        if (cl.hasOption("filebuf"))
            dcmqr.setFileBufferSize(parseInt(cl.getOptionValue("filebuf"),
                    "illegal argument of option -filebuf", 1, 10000) * KB);

        dcmqr.setPackPDV(!cl.hasOption("pdv1"));
        dcmqr.setTcpNoDelay(!cl.hasOption("tcpdelay"));
        dcmqr.setMaxOpsInvoked(cl.hasOption("async") ? parseInt(cl
                .getOptionValue("async"), "illegal argument of option -async", 0, 0xffff) : 1);

        dcmqr.setMaxOpsPerformed(cl.hasOption("cstoreasync") ? parseInt(cl
                .getOptionValue("cstoreasync"), "illegal argument of option -cstoreasync", 0, 0xffff) : 0);

        if (cl.hasOption("C"))
            dcmqr.setCancelAfter(parseInt(cl.getOptionValue("C"), "illegal argument of option -C", 1, Integer.MAX_VALUE));
        if (cl.hasOption("lowprior"))
            dcmqr.setPriority(CommandUtils.LOW);
        if (cl.hasOption("highprior"))
            dcmqr.setPriority(CommandUtils.HIGH);

        if (cl.hasOption("cstore")) {
            String[] storeTCs = cl.getOptionValues("cstore");
            for (String storeTC : storeTCs) {
                String cuid;
                String[] tsuids;
                int colon = storeTC.indexOf(':');
                if (colon == -1) {
                    cuid = storeTC;
                    tsuids = DEF_TS;
                } else {
                    cuid = storeTC.substring(0, colon);
                    String ts = storeTC.substring(colon+1);
                    try {
                        tsuids = TS.valueOf(ts).uids;
                    } catch (IllegalArgumentException e) {
                        tsuids = ts.split(",");
                    }
                }
                try {
                    cuid = CUID.valueOf(cuid).uid;
                } catch (IllegalArgumentException e) {
                    // assume cuid already contains UID
                }
                dcmqr.addStoreTransferCapability(cuid, tsuids);
            }
            if (cl.hasOption("cstoredest"))
                dcmqr.setStoreDestination(cl.getOptionValue("cstoredest"));
        }

        dcmqr.setCGet(cl.hasOption("cget"));

        if (cl.hasOption("cmove"))
            dcmqr.setMoveDest(cl.getOptionValue("cmove"));

        if (cl.hasOption("evalRetrieveAET"))
            dcmqr.setEvalRetrieveAET(true);
        // ...
        /*
        if (cl.hasOption("P"))
            dcmqr.setQueryLevel(QueryRetrieveLevel.QueryRetrieveLevelEnum.PATIENT);
        else if (cl.hasOption("S"))
            dcmqr.setQueryLevel(QueryRetrieveLevel.QueryRetrieveLevelEnum.SERIES);
        else if (cl.hasOption("I"))
            dcmqr.setQueryLevel(QueryRetrieveLevel.QueryRetrieveLevelEnum.IMAGE);
        else
            dcmqr.setQueryLevel(QueryRetrieveLevel.QueryRetrieveLevelEnum.STUDY);
        */
        // QRLifecycle2
        if (cl.hasOption("P"))
            dcmqr.setQueryLevel(QRLifecycle2.QueryRetrieveLevel.PATIENT);
        else if (cl.hasOption("S"))
            dcmqr.setQueryLevel(QRLifecycle2.QueryRetrieveLevel.SERIES);
        else if (cl.hasOption("I"))
            dcmqr.setQueryLevel(QRLifecycle2.QueryRetrieveLevel.IMAGE);
        else
            dcmqr.setQueryLevel(QRLifecycle2.QueryRetrieveLevel.STUDY);

        if (cl.hasOption("noextneg"))
            dcmqr.setNoExtNegotiation(true);
        if (cl.hasOption("rel"))
            dcmqr.setRelationQR(true);
        if (cl.hasOption("datetime"))
            dcmqr.setDateTimeMatching(true);
        if (cl.hasOption("fuzzy"))
            dcmqr.setFuzzySemanticPersonNameMatching(true);

        if (!cl.hasOption("P")) {
            if (cl.hasOption("retall"))
                dcmqr.addPrivate(
                        UID.PrivateStudyRootQueryRetrieveInformationModelFIND);
            if (cl.hasOption("blocked"))
                dcmqr.addPrivate(
                        UID.PrivateBlockedStudyRootQueryRetrieveInformationModelFIND);
            if (cl.hasOption("vmf"))
                dcmqr.addPrivate(
                        UID.PrivateVirtualMultiframeStudyRootQueryRetrieveInformationModelFIND);
        }

        if (cl.hasOption("q")) {
            String[] matchingKeys = cl.getOptionValues("q");
            for (int i = 1; i < matchingKeys.length; i++, i++)
                dcmqr.addMatchingKey(Tag.toTagPath(matchingKeys[i - 1]), matchingKeys[i]);
        }

        if (cl.hasOption("r")) {
            String[] returnKeys = cl.getOptionValues("r");
            for (int i = 0; i < returnKeys.length; i++)
                dcmqr.addReturnKey(Tag.toTagPath(returnKeys[i]));
        }

        dcmqr.configureTransferCapability(cl.hasOption("ivrle"));

        int repeat = cl.hasOption("repeat") ? parseInt(cl.getOptionValue("repeat"),
                "illegal argument of option -repeat", 1, Integer.MAX_VALUE) : 0;

        int interval = cl.hasOption("repeatdelay") ? parseInt(cl.getOptionValue("repeatdelay"),
                "illegal argument of option -repeatdelay", 1, Integer.MAX_VALUE) : 0;

        boolean closeAssoc = cl.hasOption("closeassoc");

        if (cl.hasOption("tls")) {
            String cipher = cl.getOptionValue("tls");
            if ("NULL".equalsIgnoreCase(cipher)) {
                dcmqr.setTlsWithoutEncyrption();
            } else if ("3DES".equalsIgnoreCase(cipher)) {
                dcmqr.setTls3DES_EDE_CBC();
            } else if ("AES".equalsIgnoreCase(cipher)) {
                dcmqr.setTlsAES_128_CBC();
            } else {
               //exit("Invalid parameter for option -tls: " + cipher);
            }

            if (cl.hasOption("nossl2")) {
                dcmqr.disableSSLv2Hello();
            }

            dcmqr.setTlsNeedClientAuth(!cl.hasOption("noclientauth"));

            if (cl.hasOption("keystore")) {
                dcmqr.setKeyStoreURL(cl.getOptionValue("keystore"));
            }

            if (cl.hasOption("keystorepw")) {
                dcmqr.setKeyStorePassword(
                        cl.getOptionValue("keystorepw"));
            }

            if (cl.hasOption("keypw")) {
                dcmqr.setKeyPassword(cl.getOptionValue("keypw"));
            }

            if (cl.hasOption("truststore")) {
                dcmqr.setTrustStoreURL(
                        cl.getOptionValue("truststore"));
            }

            if (cl.hasOption("truststorepw")) {
                dcmqr.setTrustStorePassword(
                        cl.getOptionValue("truststorepw"));
            }

            long t1 = System.currentTimeMillis();
            try {
                dcmqr.initTLS();
            } catch (Exception e) {
                System.err.println("ERROR: Failed to initialize TLS context:" + e.getMessage());
                //System.exit(2);
            }
            long t2 = System.currentTimeMillis();
            LOG.info("Initialize TLS context in {} s", Float.valueOf((t2 - t1) / 1000f));
        }

        try
        {
            dcmqr.start();
        }
        catch (Exception e)
        {
            System.err.println("ERROR: Failed to start server for receiving " + "requested objects:" + e.getMessage());
            //System.exit(2);
            //return result;
            throw new Exception("QR ERROR: Failed to start server for receiving " + "requested objects:" + e.getMessage());
        }

        try
        {
            long t1 = System.currentTimeMillis();
            try
            {
                dcmqr.open();
            }
            catch (Exception e)
            {
                LOG.error("Failed to establish association:", e);
                System.out.println("Failed to establish association: " + e.getMessage());
                //System.exit(2);
                //return result;
                throw new Exception("QR ERROR: Failed to establish association: " + e.getMessage());
            }
            long t2 = System.currentTimeMillis();
            LOG.info("Connected to {} in {} s", remoteAE, Float.valueOf((t2 - t1) / 1000f));
            System.out.println("Connected to "+remoteAE+" in "+Float.valueOf((t2 - t1) / 1000f)+" s");

            for (;;)
            {
                result = dcmqr.query();

                // PAB ==================================
                /*
                System.out.println( "<result>" );
                for (DicomObject o : result)
                {
                    System.out.println( "  <objeto>" );

                    //System.out.println( o.toString() ); // Imprime todo el objeto con sus contenidos.

                    // Configuracion de tags que quiero
                    Map<String,Integer> tags = new HashMap<String,Integer>();
                    tags.put("Patient Name TAG", Integer.parseInt(patientNameTag,16));
                    tags.put("Study Instance UID TAG", Integer.parseInt(studyInstanceUIDTag,16));
                    tags.put("Series Instance UID TAG", Integer.parseInt(seriesInstanceUIDTag,16));
                    tags.put("SOP Instance UID TAG", Integer.parseInt(sopInstanceUIDTag,16));

                    tags.put("PN", Integer.parseInt("00100010",16));
                    tags.put("PID", Integer.parseInt("00100020",16));
                    tags.put("PID Issuer", Integer.parseInt("00100021",16));
                    tags.put("PID Type", Integer.parseInt("00100022",16));

                    if ( o instanceof BasicDicomObject )
                    {
                        Iterator<String> itags = tags.keySet().iterator();
                        String tagname;
                        DicomElement elem;
                        while (itags.hasNext())
                        {
                            tagname = itags.next();

                            elem = ((BasicDicomObject)o).get( tags.get(tagname) );

                            System.out.println( "    " + tagname + ": " + elem );
                        }
                    }
                    System.out.println( "  </objeto>" );
                }

                System.out.println( "</result>" );
                */
                // /PAB =================================

                long t3 = System.currentTimeMillis();

//                LOG.info("Received {} matching entries in {} s", Integer.valueOf(result.size()), Float.valueOf((t3 - t2) / 1000f));
                System.out.println("Received "+Integer.valueOf(result.size())+" matching entries in "+Float.valueOf((t3 - t2) / 1000f)+" s" );

                if (dcmqr.isCMove() || dcmqr.isCGet())
                {
                    if (dcmqr.isCMove())
                        dcmqr.move(result);
                    else
                        dcmqr.get(result);
                    long t4 = System.currentTimeMillis();
                    System.out.println("Retrieved "+Integer.valueOf(dcmqr.getTotalRetrieved())+
                                       " objects (warning: "+Integer.valueOf(dcmqr.getWarning())+
                                       ", failed: "+Integer.valueOf(dcmqr.getFailed())+
                                       ") in "+Float.valueOf((t4 - t3) / 1000f)+"s");

                    LOG.info("Retrieved {} objects (warning: {}, failed: {}) in {}s",
                             new Object[] {
                               Integer.valueOf( dcmqr.getTotalRetrieved() ),
                               Integer.valueOf(dcmqr.getWarning()),
                               Integer.valueOf(dcmqr.getFailed()),
                               Float.valueOf((t4 - t3) / 1000f) }
                            );
                }

                if (repeat == 0 || closeAssoc)
                {
                    try
                    {
                        dcmqr.close();
                    }
                    catch (InterruptedException e)
                    {
                        //LOG.error(e.getMessage(), e);
                        System.out.println(e.getMessage());
                    }
                    //LOG.info("Released connection to {}",remoteAE);
                    System.out.println("Released connection to "+remoteAE);
                }

                if (repeat-- == 0)
                    break;

                Thread.sleep(interval);
                long t4 = System.currentTimeMillis();

                dcmqr.open();

                t2 = System.currentTimeMillis();
                LOG.info("Reconnect or reuse connection to {} in {} s", remoteAE, Float.valueOf((t2 - t4) / 1000f));
                System.out.println("Reconnect or reuse connection to "+remoteAE+" in "+Float.valueOf((t2 - t4) / 1000f)+" s");
            }
        }
        catch (IOException e)
        {
            //LOG.error(e.getMessage(), e);
            System.out.println(e.getMessage());
        }
        catch (InterruptedException e)
        {
            System.out.println(e.getMessage());
            //LOG.error(e.getMessage(), e);
        }
        catch (ConfigurationException e)
        {
            System.out.println(e.getMessage());
            //LOG.error(e.getMessage(), e);
        }
        finally
        {
            dcmqr.stop();
        }

        return result;
    }

    /**
     * @param args
     */
    public static void main(String[] args)
    {
        // -L QRSCUCHE:44445 SERVERAE@127.0.0.1:33333 -qStudyDate=20010105
        // -qModalitiesInStudy=CT -r 0020000e -r 00080018 -I

        // Local address ans listening port of local App Entity
        // -L QRSCUCHE:44445

        // Remote server
        // SERVERAE@127.0.0.1:33333

        // Fecha del estudio
        // -qStudyDate=20010105

        // Modalidades en el estudio
        // -qModalitiesInStudy=CT

        // Additional Return Key (Series Instance UID TAG)
        // -r 0020000e

        // Additional Return Key (SOP Instance UID TAG)
        // -r 00080018

        // Hace la consulta a nivel de Imagen, tambien puede hacer a nivel de
        // Serie o Paciente. Por defecto el nivle es estudio.
        // -I

/*
        ArrayList<String> modalitiesInStudy = new ArrayList<String>();
        modalitiesInStudy.add("CT");

        ArrayList<String> additionalReturnKeys = new ArrayList<String>();
        additionalReturnKeys.add("0020000e");
        additionalReturnKeys.add("00080018");
        additionalReturnKeys.add("00100010"); // Patients name
        additionalReturnKeys.add("00100020"); // Patient id
        additionalReturnKeys.add("00100021"); // Issuer of Patient id
        additionalReturnKeys.add("00100022"); // Type of Patient id
*/

        //System.out.println( new Date(2000, 1, 5) );

        ////////// PRUEBAS COMEPA //////////////

        // NO ME FUNKA LA BUSQUEDA POR PACIENTES
//      CommandLine cl = make_patient_studies_query(
//            "QRSCUCHE", 44445,
//            "SM_COMEPA", "172.28.14.21", 4444,
//      "richard", null,
//      null,
//      null, null, null,
//      null, null, null); //new Date(2009,9,1), new Date(2009,9,2)); //new Date(2009-1900,8-1,10), new Date(2009-1900,8-1,30) );

      //System.out.println( new Date(2009-1900,8-1,1) );

//CommandLine cl = make_studies_query(
//      "QRSCUCHE", 44445,
//      "SM_COMEPA", "172.28.14.21", 4444,
//      null, "5067",
//      new Date(2009,8,10), null ); // hace transformacion de la fecha internamente.

//CommandLine cl = make_study_series_query(
//      "QRSCUCHE", 44445,
//      "SM_COMEPA", "172.28.14.21", 4444,
//      "5067", null, //String studyId, String studyUID,
//      null, null, //Date studyDateStart, Date studyDateEnd,
//      "CR" );

/*
CommandLine cl = make_serie_images_query(
        "QRSCUCHE", 44445,
        "SM_COMEPA", "172.28.14.21", 4444,
        null, null ); // COMEPA no funka con serieUID> "12979"
*/
        CommandLine cl = make_serie_images_query(
                "QRSCUCHE", 44445,
                "SM_COMEPA", "192.168.231.114", 4444,
                "1.2.840.113564.172282448.2010081015463135948",
                "1.2.840.113564.172282448.2010081016025284360",
                null );

         //////////PRUEBAS COMEPA //////////////

        /*
        CommandLine cl = make_query_retrieve(
                "QRSCUCHE", 44445,
                "SERVERAE", "127.0.0.1", 33333,
                null, "MISTER",
                null, //new Date(2000,1,5), //new Date(2001,1,5),
                null, //new Date(2002,1,5),
                modalitiesInStudy,
                additionalReturnKeys,
                Nivel._SERIES
                );
        */


//        CommandLine cl = make_patient_studies_query(
//                "QRSCUCHE", 44445,
//                "SERVERAE", "127.0.0.1", 33333,
//                null, "MISTER",
//                null,
//                null, null, null,
//                null, null, null);

//        CommandLine cl = make_studies_query(
//                "QRSCUCHE", 44445,
//                "SERVERAE", "127.0.0.1", 33333,
//                "2178309", null,
//                null, null );

//        CommandLine cl = make_study_series_query(
//                "QRSCUCHE", 44445,
//                "SERVERAE", "127.0.0.1", 33333,
//                null, null, //String studyId, String studyUID,
//                null, null, //Date studyDateStart, Date studyDateEnd,
//                "CT" );

        List<DicomObject> result = null;
        try
        {
            result = send_query( cl );
        }
        catch (Exception e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        System.out.println(result);

//        String[] argsss = cl.getArgs();
//        for (int i=0; i<argsss.length; i++)
//           System.out.println( argsss[i] );

        // TODO: como hago para pedir de varias modalidades?
//        String[] args2 = { "-L", "QRSCUCHE:44445", "SERVERAE@127.0.0.1:33333",
//                "-qStudyDate=20010105", "-qModalitiesInStudy=CT", "-r",
//                "0020000e", "-r", "00080018", "-r", "00100010", "-I" };
//        CommandLine cm = parse(args2);

//        String[] args_maciel = { "-L", "QRSCUCHE:44445", "DCM4CHEE@192.168.118.16:11112",
//                "-qStudyDate=20090609", "-qModalitiesInStudy=CT", "-r",
//                "0020000e", "-r", "00080018", "-r", "00100010", "-I" };
//        CommandLine cm_maciel = parse(args_maciel);

        // Imprime lso contenids de result en "bruto" como XML.
        //XStream xstream = new XStream();
        //String xml = xstream.toXML(cl);
        //System.out.print(xml);


// dcmqr.bat -L QRSCUCHE:44445 SERVERAE@127.0.0.1:33333 -qStudyDate=20000105-20090801 -qModalitiesInStudy=CT -r 0020000e -r 00080018 -I

// dcmqr.bat -L QRSCUCHE:44445 SERVERAE@127.0.0.1:33333
// -q0020000D=1.2.840.113619.2.30.1.1762295590.1623.978668949.886
// -q0020000E=1.2.840.113619.2.30.1.1762295590.1623.978668949.890 -r 0020000e -r 00080018 -S

/*
        String[] args2 = { "-L", "QRSCUCHE:44445", "SERVERAE@127.0.0.1:33333",
                           "-qStudyDate=20000105-20020105", "-qModalitiesInStudy=CT",
                           "-r", "0020000e", "-r", "00080018", "-S" };
        CommandLine cmf1f2 = parse(args2);

        System.out.println("========================================");
        System.out.println( cmf1f2.getArgList() );

        Option[] options = cmf1f2.getOptions();
        for (int i=0; i<options.length; i++)
        {
            //System.out.println( options[i].getArgName() +" "+ options[i].getValue() );
            System.out.println(  options[i].getArgName() +" "+ options[i].getValuesList() );
        }
        System.out.println("========================================");

        List<DicomObject> result = send_query( cmf1f2 );
*/

        // Imprime los contenids de result en "bruto" como XML.
        //XStream xstream = new XStream();
        //String xml = xstream.toXML(result);
        //System.out.print(xml);

        // PAB ==================================
        System.out.println( "<result>" );
        for (DicomObject o : result)
        {
            System.out.println( "  <objeto>" );

            //System.out.println( o.toString() ); // Imprime todo el objeto con sus contenidos.

            // Configuracion de tags que quiero
            /*
            Map<String,Integer> tags = new HashMap<String,Integer>();
            tags.put("PatientName",       Tag.PatientName);
            tags.put("PatientSex",        Tag.PatientSex);
            tags.put("PatientBirthTime",  Tag.PatientBirthTime);
            tags.put("PersonName",        Tag.PersonName); // nadie hace query por esta tag
            tags.put("StudyInstanceUID",  Tag.StudyInstanceUID);
            tags.put("SeriesInstanceUID", Tag.SeriesInstanceUID); //Integer.parseInt(seriesInstanceUIDTag,16));
            tags.put("SOPInstanceUID",    Tag.SOPInstanceUID);
            tags.put("PatientID",         Tag.PatientID);
            tags.put("IssuerOfPatientID", Tag.IssuerOfPatientID);
            tags.put("TypeOfPatientID",   Tag.TypeOfPatientID);
            tags.put("StudyID",           Tag.StudyID);
            tags.put("SeriesNumber",      Tag.SeriesNumber); // numero de la serie en las series de un estudio

            tags.put("NumberOfPatientRelatedStudies",   Tag.NumberOfPatientRelatedStudies);   // # estudios
            tags.put("NumberOfPatientRelatedSeries",    Tag.NumberOfPatientRelatedSeries);    // # series
            tags.put("NumberOfPatientRelatedInstances", Tag.NumberOfPatientRelatedInstances); // # imagenes

            tags.put("NumberOfStudyRelatedSeries", Tag.NumberOfStudyRelatedSeries );       // # series en estudio
            tags.put("NumberOfStudyRelatedInstances", Tag.NumberOfStudyRelatedInstances ); // # imagenes en estudio

            tags.put("NumberOfSeriesRelatedInstances", Tag.NumberOfSeriesRelatedInstances ); // # imagenes en la serie

            tags.put("ModalitiesInStudy", Tag.ModalitiesInStudy );
            tags.put("Modality", Tag.Modality );

            if ( o instanceof BasicDicomObject )
            {
                Iterator<String> itags = tags.keySet().iterator();
                String tagname;
                DicomElement elem;
                while (itags.hasNext())
                {
                    tagname = itags.next();

                    elem = ((BasicDicomObject)o).get( tags.get(tagname) );

                    if (elem != null && !elem.isEmpty())
                    {
                       System.out.println( "    " + tagname + ": " + elem );
                       System.out.println( "        valor: " +  new String( elem.getBytes() ).trim() ); // valor del atributo
                   }
                }
            }
            */

            // Iteracion por todos los elements del DicomObject.
            Iterator<DicomElement> i = o.iterator();
            DicomElement iter_elem;
            while (i.hasNext())
            {
                iter_elem = i.next();
                //iter_elem.vr(); // DICOM Value Representation
                //iter_elem.tag(); // DICOM Tag
                //String value = "";
                //((SimpleDicomElement)iter_elem). no puedo acceder al append para ver el valor...



                //System.out.print( iter_elem.getClass() );
                System.out.print( "    <element>\n"   +
                                  //"      <value_rep>" + iter_elem.vr() + "</value_rep>\n" +
                                  "      <name>" + o.nameOf( iter_elem.tag() ) + "</name>\n" +
                                  //"      <tag>"       + Integer.toHexString( iter_elem.tag() ) +"</tag>\n" +
                                  "      <value>"     + new String( iter_elem.getBytes() ).trim() +"</value>\n" +
                                  "    </element>\n" );

                //System.out.print( "<element>\n" + iter_elem.toString() + "\n</element>\n" );
            }


            System.out.println( "  </objeto>" );
        }

        System.out.println( "</result>" );
        // /PAB =================================


        //String xml = xstream.toXML(result);
        //System.out.print(xml);
    }

}
