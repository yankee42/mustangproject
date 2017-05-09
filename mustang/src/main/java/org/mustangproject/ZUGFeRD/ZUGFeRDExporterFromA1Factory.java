package org.mustangproject.ZUGFeRD;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.preflight.PreflightDocument;
import org.apache.pdfbox.preflight.ValidationResult;
import org.apache.pdfbox.preflight.exception.ValidationException;
import org.apache.pdfbox.preflight.parser.PreflightParser;
import org.apache.pdfbox.preflight.utils.ByteArrayDataSource;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.AdobePDFSchema;
import org.apache.xmpbox.schema.DublinCoreSchema;
import org.apache.xmpbox.schema.PDFAIdentificationSchema;
import org.apache.xmpbox.schema.XMPBasicSchema;
import org.apache.xmpbox.type.BadFieldValueException;
import org.apache.xmpbox.xml.XmpSerializer;

import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.xml.bind.JAXBException;
import javax.xml.transform.TransformerException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.GregorianCalendar;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ZUGFeRDExporterFromA1Factory {
    private static final String VERSION_STR = "1.3.0";

    private boolean ignoreA1Errors = false;
    // BASIC, COMFORT etc - may be set from outside.
    private String ZUGFeRDConformanceLevel = null;
    private String conformanceLevel = "U";

    public ZUGFeRDExporterFromA1Factory setIgnoreA1Errors(boolean ignoreA1Errors) {
        this.ignoreA1Errors = ignoreA1Errors;
        return this;
    }

    /**
     * Makes A PDF/A3a-compliant document from a PDF-A1 compliant document (on
     * the metadata level, this will not e.g. convert graphics to JPG-2000)
     *
     */
    public ZUGFeRDExporter loadFromPDFA1(String filename,
                                         String producer,
                                         String creator,
                                         boolean attachZugferdHeaders) throws IOException, TransformerException, JAXBException {

        if (!ignoreA1Errors && !isValidA1(new FileDataSource(filename))) {
            throw new IOException("File is not a valid PDF/A-1 input file");
        }
        PDDocument doc = PDDocument.load(new File(filename));
        makePDFA3compliant(doc, producer, creator, attachZugferdHeaders);
        return new ZUGFeRDExporter(doc);
    }

    public ZUGFeRDExporter loadFromPDFA1(byte[] content,
                                         String producer,
                                         String creator,
                                         boolean attachZugferdHeaders) throws IOException, TransformerException, JAXBException {

        if (!ignoreA1Errors && !isValidA1(new ByteArrayDataSource(new ByteArrayInputStream(content)))) {
            throw new IOException("File is not a valid PDF/A-1 input file");
        }
        PDDocument doc = PDDocument.load(content);
        makePDFA3compliant(doc, producer, creator, attachZugferdHeaders);
        return new ZUGFeRDExporter(doc);
    }

    public ZUGFeRDExporter loadFromPDFA1(InputStream file,
                                         String producer, String creator, boolean attachZugferdHeaders)
        throws IOException, TransformerException, JAXBException {

        return loadFromPDFA1(readAllBytes(file), producer, creator, attachZugferdHeaders);
    }

    private static byte[] readAllBytes(final InputStream file) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n = 0;
        while ((n = file.read(buf)) >= 0)
            baos.write(buf, 0, n);
        return baos.toByteArray();
    }

    private void makePDFA3compliant(PDDocument doc, String producer,
                                           String creator, boolean attachZugferdHeaders) throws IOException,
        TransformerException {
        String fullProducer = producer + " (via mustangproject.org " + VERSION_STR + ")";

        PDDocumentCatalog cat = doc.getDocumentCatalog();
        PDMetadata metadata = new PDMetadata(doc);
        cat.setMetadata(metadata);
        XMPMetadata xmp = XMPMetadata.createXMPMetadata();


        PDFAIdentificationSchema pdfaid = new PDFAIdentificationSchema(xmp);

        xmp.addSchema(pdfaid);

        DublinCoreSchema dc = xmp.createAndAddDublinCoreSchema();

        dc.addCreator(creator);

        XMPBasicSchema xsb = xmp.createAndAddXMPBasicSchema();

        xsb.setCreatorTool(creator);
        xsb.setCreateDate(GregorianCalendar.getInstance());
        // PDDocumentInformation pdi=doc.getDocumentInformation();
        PDDocumentInformation pdi = new PDDocumentInformation();
        pdi.setProducer(fullProducer);
        pdi.setAuthor(creator);
        doc.setDocumentInformation(pdi);

        AdobePDFSchema pdf = xmp.createAndAddAdobePDFSchema();
        pdf.setProducer(fullProducer);

        /*
        * // Mandatory: PDF/A3-a is tagged PDF which has to be expressed using
        * a // MarkInfo dictionary (PDF A/3 Standard sec. 6.7.2.2) PDMarkInfo
        * markinfo = new PDMarkInfo(); markinfo.setMarked(true);
        * doc.getDocumentCatalog().setMarkInfo(markinfo);
        */
        /*
        *
        * To be on the safe side, we use level B without Markinfo because we
        * can not guarantee that the user correctly tagged the templates for
        * the PDF.
        */
        try {
            pdfaid.setConformance(conformanceLevel);//$NON-NLS-1$ //$NON-NLS-1$
        } catch (BadFieldValueException ex) {
            Logger.getLogger(ZUGFeRDExporter.class.getName()).log(Level.SEVERE, null, ex);
        }

        pdfaid.setPart(3);

        if (attachZugferdHeaders) {
            addZugferdXMP(xmp); /*
								 * this is the only line where we do something
								 * Zugferd-specific, i.e. add PDF metadata
								 * specifically for Zugferd, not generically for
								 * a embedded file
								 */

        }

        XmpSerializer serializer = new XmpSerializer();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        serializer.serialize(xmp, baos, false);
        metadata.importXMPMetadata( baos.toByteArray() );
    }

    public boolean isValidA1(DataSource dataSource) {
        try {
            PreflightParser parser = new PreflightParser(dataSource);
            return getA1ParserValidationResult(parser);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return false;
        }
    }

    /**
     * * This will add both the RDF-indication which embedded file is Zugferd
     * and the neccessary PDF/A schema extension description to be able to add
     * this information to RDF
     *
     * @param metadata
     */
    private void addZugferdXMP(XMPMetadata metadata) {

        XMPSchemaZugferd zf = new XMPSchemaZugferd(metadata, ZUGFeRDConformanceLevel);

        metadata.addSchema(zf);

        XMPSchemaPDFAExtensions pdfaex = new XMPSchemaPDFAExtensions(metadata);

        metadata.addSchema(pdfaex);

    }

    /**
     * Sets the ZUGFeRD conformance level (override).
     *
     * @param ZUGFeRDConformanceLevel
     *            the new conformance level
     */
    public void setZUGFeRDConformanceLevel(String ZUGFeRDConformanceLevel) {
        this.ZUGFeRDConformanceLevel = ZUGFeRDConformanceLevel;
    }

    private boolean getA1ParserValidationResult(PreflightParser parser) {
        ValidationResult result = null;

        try {

			/*
			 * Parse the PDF file with PreflightParser that inherits from the
			 * NonSequentialParser. Some additional controls are present to
			 * check a set of PDF/A requirements. (Stream length consistency,
			 * EOL after some Keyword...)
			 */
            parser.parse();

			/*
			 * Once the syntax validation is done, the parser can provide a
			 * PreflightDocument (that inherits from PDDocument) This document
			 * process the end of PDF/A validation.
			 */
            PreflightDocument document = parser.getPreflightDocument();
            document.validate();

            // Get validation result
            result = document.getResult();
            document.close();

        } catch (ValidationException e) {
			/*
			 * the parse method can throw a SyntaxValidationException if the PDF
			 * file can't be parsed. In this case, the exception contains an
			 * instance of ValidationResult
			 */
            return false;
        } catch (IOException e) {
            return false;
        }

        // display validation result
        return result.isValid();
    }

    /**
     * All files are PDF/A-3, setConformance refers to the level conformance.
     *
     * PDF/A-3 has three coformance levels, called "A", "U" and "B".
     *
     * PDF/A-3-B where B means only visually preservable, U -standard for
     * Mustang- means visually and unicode preservable and A means full
     * compliance, i.e. visually, unicode and structurally preservable and
     * tagged PDF, i.e. useful metainformation for blind people.
     *
     * Feel free to pass "A" as new level if you know what you are doing :-)
     *
     *
     */
    public void setConformanceLevel(String newLevel) {
        conformanceLevel = newLevel;
    }
}
