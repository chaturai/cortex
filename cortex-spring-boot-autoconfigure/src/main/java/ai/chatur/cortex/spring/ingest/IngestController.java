package ai.chatur.cortex.spring.ingest;

import ai.chatur.cortex.IngestService;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cortex")
public class IngestController {
  @Autowired AssertionRepository assertionRepository;
  @Autowired IngestService ingestService;

  @GetMapping
  String inspect() {
    OutputStream os = new ByteArrayOutputStream();
    assertionRepository.writeAssertions(os);
    return os.toString();
  }

  @GetMapping("delete")
  String delete(@RequestParam("branch") String branch) {
    if (ingestService.reject(branch)) return "OK";
    else return "NOT_FOUND";
  }
}
