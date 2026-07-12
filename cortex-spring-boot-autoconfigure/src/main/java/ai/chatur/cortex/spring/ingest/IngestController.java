package ai.chatur.cortex.spring.ingest;

import ai.chatur.cortex.Cortex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

@Controller
public class IngestController {
  @Autowired Cortex cortex;
}
