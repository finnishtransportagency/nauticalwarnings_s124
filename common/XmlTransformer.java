package fi.liikennevirasto.winvis.common;

import fi.liikennevirasto.commons.SecureishXml;
import fi.liikennevirasto.routelib.HandlerUtils;
import fi.liikennevirasto.routelib.RTZHandlingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;


/**
 * Transforms XML in standard manner, without String 'hacking'
 */
@Component
public class XmlTransformer {

    private static final Logger LOG = LoggerFactory.getLogger(XmlTransformer.class);

    /**
     * Receives XML as String object and transforms it to new XML String without XML declaration
     */
    public String xmlToStringWithoutXmlDeclaration(String xml) {
        xml = HandlerUtils.removeUTF8BOM(xml);

        StringReader str = new StringReader(xml);
        StringWriter stw = new StringWriter();

        try {
            Transformer serializer = SecureishXml.buildTransformerFactory().newTransformer();
            serializer.setOutputProperty("omit-xml-declaration", "yes");
            serializer.transform(new StreamSource(str), new StreamResult(stw));
        } catch (TransformerException e) {
            String errMsg = "Transformation of RTZ document failed " + e.getMessage();
            LOG.error(errMsg, e);
            throw new RTZHandlingException(errMsg);
        }

        return stw.toString();
    }
}
