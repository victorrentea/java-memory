package victor.training.performance.leak;

import jakarta.annotation.PostConstruct;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@SuppressWarnings({"resource", "OverlyBroadThrowsClause"})
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("leak20")
public class Leak20_LOL { //WIP
  //language=xml
  private static final String EXPECTED_XML = """
      <point><x>2</x><y>3</y></point>
      """;
  @Data
  @XmlRootElement
  public static class PointXml{
    private Integer x,y;
  }
  //language=xml
  private static final String LOL_BOMB_XML = """
        <?xml version="1.0"?>
        <!DOCTYPE lolz [
          <!ENTITY lol "lol">
          <!ELEMENT lolz (#PCDATA)>
          <!ENTITY lol1 "&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;">
          <!ENTITY lol2 "&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;">
          <!ENTITY lol3 "&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;">
          <!ENTITY lol4 "&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;">
          <!ENTITY lol5 "&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;">
          <!ENTITY lol6 "&lol5;&lol5;&lol5;&lol5;&lol5;&lol5;&lol5;&lol5;&lol5;&lol5;">
          <!ENTITY lol7 "&lol6;&lol6;&lol6;&lol6;&lol6;&lol6;&lol6;&lol6;&lol6;&lol6;">
          <!ENTITY lol8 "&lol7;&lol7;&lol7;&lol7;&lol7;&lol7;&lol7;&lol7;&lol7;&lol7;">
          <!ENTITY lol9 "&lol8;&lol8;&lol8;&lol8;&lol8;&lol8;&lol8;&lol8;&lol8;&lol8;">
        ]>
        <lolz>&lol9;</lolz>
        """;
  private final File expectedXml = new File("expected.xml");
  private final File lolBombXml = new File("lolbomb.xml");
  @PostConstruct
  public void writeXmlFilesOnDisk() throws IOException {
    Files.writeString(expectedXml.toPath(), EXPECTED_XML);
    Files.writeString(lolBombXml.toPath(), LOL_BOMB_XML);
    System.setProperty("jdk.xml.entityExpansionLimit", "200000000");
  }
  
  @PostMapping(consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
  public PointXml uploadFile(@RequestParam MultipartFile file) throws Exception {
    log.info("Uploaded file: {} ({} bytes)", file.getOriginalFilename(), file.getSize());
    PointXml pointXml = parse(new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8));
    log.info("Parsed XML: {}", pointXml);
    return pointXml;
  }

  private PointXml parse(String xml) throws Exception{

    JAXBContext jaxbContext = JAXBContext.newInstance(PointXml.class);
//    return (PointXml) jaxbContext.createUnmarshaller().unmarshal(new java.io.StringReader(xml)); // safe

    DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    Document document = db.parse(new ByteArrayInputStream(xml.getBytes()));
    return (PointXml) jaxbContext.createUnmarshaller().unmarshal(document);
  }

  @GetMapping
  public String home()  {
    // language=html
    String expectedXmlHtmlEncoded = EXPECTED_XML.replace("<","&lt;").replace(">","&gt;").replace("\n","<br/>");
    return """
          <form action='/leak20' method='post' enctype='multipart/form-data' target='_blank'>
              <input type='file' name='file' />
              <input type='submit' value='Upload'>
          </form>
          <li>Expected XML: %s<br/>
           %s
          <li>Lol Bomb XML: %s
           """.formatted(expectedXml.getAbsolutePath(), expectedXmlHtmlEncoded, lolBombXml.getAbsolutePath());
  }


}
