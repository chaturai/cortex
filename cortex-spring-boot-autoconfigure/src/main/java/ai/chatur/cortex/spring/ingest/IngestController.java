package ai.chatur.cortex.spring.ingest;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cortex")
public class CortexController {
  @Autowired AssertionRepository assertionRepository;
  @Autowired IngestService ingestService;

  @GetMapping
  String inspect() {
    OutputStream os = new ByteArrayOutputStream();
    assertionRepository.printAssertions(os);
    return os.toString();
  }

  String delete() {

  }
}
