package ai.chatur.cortex;

import java.util.List;

public record OntologyClass(String name, List<OntologyClass> subClasses) {}
