package ai.chatur.cortex;

import java.util.List;

public record OntologyClass(Term term, List<OntologyClass> subClasses) {}
