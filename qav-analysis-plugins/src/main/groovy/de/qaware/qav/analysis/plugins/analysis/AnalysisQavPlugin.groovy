package de.qaware.qav.analysis.plugins.analysis

import com.google.common.base.Preconditions
import de.qaware.qav.architecture.checker.AllComponentsImplementedChecker
import de.qaware.qav.architecture.checker.AllExplicitRulesUsedChecker
import de.qaware.qav.architecture.checker.Checker
import de.qaware.qav.architecture.checker.DependencyChecker
import de.qaware.qav.analysis.plugins.base.BasePlugin
import de.qaware.qav.doc.QavCommand
import de.qaware.qav.doc.QavPluginDoc
import de.qaware.qav.analysis.dsl.model.Analysis
import de.qaware.qav.architecture.dsl.model.Architecture
import de.qaware.qav.graph.alg.api.CycleFinder
import de.qaware.qav.graph.alg.impl.CycleFinderImpl
import de.qaware.qav.graph.api.DependencyGraph
import de.qaware.qav.graph.api.DependencyType
import de.qaware.qav.graph.filter.DependencyTypeEdgeOutFilter
import groovy.util.logging.Slf4j

/**
 * QAV Language constructs for Analysis.
 *
 * @author QAware GmbH
 */
@Slf4j
@QavPluginDoc(name = "AnalysisQavPlugin",
        description = """
                QAvalidator's main mission is to validate source code against intended architecture concepts.
                If a validation fails, QAvalidator can either issue a warning, or it can fail the build.
                """
)
class AnalysisQavPlugin extends BasePlugin {

    @Override
    void apply(Analysis analysis) {
        super.apply(analysis)

        analysis.context.IN_CYCLE = CycleFinder.IN_CYCLE

        analysis.register("findCycles", this.&findCycles)
        analysis.register("checkArchitectureRules", this.&checkArchitectureRules)
        analysis.register("checkDependencyRules", this.&checkDependencyRules)
        analysis.register("reportLeftOvers", this.&reportLeftOvers)
    }

    /**
     * Checks the input graph for cycles.
     * Creates: classesCycleGraph
     *
     * @param graph the {@link DependencyGraph} to find the cycles in
     * @param scope name of the scope, used for error messages
     */
    @QavCommand(name = "findCycles",
            description = """
                    Checks the input graph for cycles.
                    """,
            parameters = [
                    @QavCommand.Param(name = "graph", description = "the DependencyGraph to find the cycles in"),
                    @QavCommand.Param(name = "scope", description = "name of the scope, used for error messages"),
                    @QavCommand.Param(name = "filterContains", description = """
                            if `true`, filters out all dependencies of type CONTAINS.
                            In most cases, it does not make sense to include these dependencies in the search for cycles.
                            Defaults to `true`. So if they really should be included, set this to false explicitly.
                            """)
            ],
            result = """
                    The given graph, filtered to only those nodes which are part of a cycle. 
                    I.e., if the resulting graph is empty, then the original graph is free of cycles.
                    If it is not free of cycles, QAvalidator issues a violation and marks the analysis step as _failed_.
                    """
    )
    DependencyGraph findCycles(DependencyGraph graph, String scope, boolean filterContains = true) {
        Preconditions.checkNotNull(graph, "graph");
        Preconditions.checkNotNull(scope, "scope");

        DependencyGraph relevantGraph = filterContains ? graph.filter(new DependencyTypeEdgeOutFilter(DependencyType.CONTAINS)) : graph

        CycleFinder cycleFinder = new CycleFinderImpl(relevantGraph)
        if (cycleFinder.hasCycles()) {
            cycleFinder.cycles.each {
                analysis.sonarError("${scope.toUpperCase()} CYCLE with ${it.size()} nodes: ${it.toString()}")
            }

            int totalNodes = cycleFinder.cycles.flatten().size()
            analysis.violation("Cycles: ${cycleFinder.cycles.size()} cylces with ${totalNodes} nodes")
        }

        return graph.filter(analysis.filter("cycleFilter"))
    }

    /**
     * Creates and uses new {@link Checker}s to validate the graph against the given architecture.
     *
     * @param architectureGraph the graph where we want to find rule violations
     * @param architecture the {@link Architecture} to check against
     */
    @QavCommand(name = "checkArchitectureRules",
            description = """
                    Validate against a specific architecture.
                    Checks the given graph according to the given architecture. It checks these rules:

                    . Checks that all edges are allowed, i.e. that for each edge there is at least one rule which
                      justifies the existence of that edge
                    . Checks that all components are actually implemented. I.e. we don't want to have components which
                      are defined on the architecture level, but don't have corresponding classes in the code base.
                    . Checks that all rules which are explicitly defined are used in the code base.

                    For each rule which is not fulfilled, the command issues an error and writes a line into the 
                    SonarQube error file for the SonarQube analysis.
            """,
            parameters = [
                    @QavCommand.Param(name = "architectureGraph", description = "the graph where we want to find rule violations"),
                    @QavCommand.Param(name = "architecture", description = "Architecture to check against")
            ]
    )
    void checkArchitectureRules(DependencyGraph architectureGraph, Architecture architecture) {
        List<Checker> checkers = [new DependencyChecker(architectureGraph, architecture),
                                  new AllComponentsImplementedChecker(architectureGraph, architecture),
                                  new AllExplicitRulesUsedChecker(architectureGraph, architecture)
        ]

        checkers
                .grep {checker -> !checker.isOk()}
                .each {checker -> reportCheckerViolation(checker, architecture)}
    }

    /**
     * Creates and uses a new {@link DependencyChecker} to validate the graph against the given architecture.
     *
     * @param architectureGraph the graph where we want to find rule violations
     * @param architecture the {@link Architecture} to check against
     *
     * @since 1.2.0
     */
    @QavCommand(name = "checkDependencyRules",
            description = """
                    Validate against a specific architecture.
                    Checks the given graph according to the given architecture. It checks this rule:

                    . Checks that all edges are allowed, i.e. that for each edge there is at least one rule which
                      justifies the existence of that edge.

                    If the rule is not fulfilled, the command issues an error and writes a line into the SonarQube error 
                    file for the SonarQube analysis.
            """,
            parameters = [
                    @QavCommand.Param(name = "architectureGraph", description = "the graph where we want to find rule violations"),
                    @QavCommand.Param(name = "architecture", description = "Architecture to check against")
            ]
    )
    void checkDependencyRules(DependencyGraph architectureGraph, Architecture architecture) {
        Checker checker = new DependencyChecker(architectureGraph, architecture)

        if (!checker.isOk()) {
            reportCheckerViolation(checker, architecture)
        }
    }

    private void reportCheckerViolation(Checker checker, Architecture architecture) {
        def msg = "${checker.class.simpleName}: ${checker.violationMessages.size()} VIOLATIONS in architecture view ${architecture.name}: ${checker.violationMessage}"
        analysis.sonarError(msg)
        analysis.violation("Architecture Checker found violations: ${msg}")
    }

    /**
     * Reports the classes not covered in the  DSL architecture description.
     *
     * @param nodeName name of the node collecting all not-matched classes. Defaults to <code>"Rest"</code>
     */
    @QavCommand(name = "reportLeftOvers",
            description = """
                    WARNING: As of QAvalidator 1.2.0, this command is deprecated. 
                    The command `createArchitectureView` already reports unmapped classes.
                    Remove the component `Rest` (or whatever it is called) from your architecture DSL, and remove the
                    call to `reportLeftOvers` from the analysis DSL script.
                    
                    _Old description:_
                    
                    _Reports the classes not covered in the  DSL architecture description._

                    _There is a convention that the last component in the architecture file is called "Rest", and its
                    regular expression assigns all classes. Since the assignment of classes to architecture components
                    happens in the order of their appearance in the Architecture DSL file, only those classes which do
                    not have a matching component are really assigned._
                    """,
            parameters = @QavCommand.Param(name = "nodeName",
                    description = "name of the node collecting all not-matched classes. Defaults to `Rest`")
    )
    void reportLeftOvers(String nodeName = "Rest") {
        log.warn("DEPRECATED (since 1.2.0):  The command `createArchitectureView` already reports unmapped classes. " +
                "Remove both the component ${nodeName} from your architecture DSL, and the call to `reportLeftOvers` from the analysis DSL script.")
        def leftOverClasses
        def restNode = context.dependencyGraph.getNode(nodeName)
        if (restNode) {
            // only use the missed classes - don't print out components which have relations to those missed classes:
            leftOverClasses = context.dependencyGraph.getOutgoingEdges(restNode)
                    .grep {it.dependencyType == DependencyType.CONTAINS}
                    .collect {dep -> dep.target.name}

            if (leftOverClasses) {
                log.error("The architecture definition misses {} classes:", leftOverClasses.size())
                leftOverClasses
                        .each {String name -> log.error("    * " + name)}

                analysis.sonarWarn("DEFINED LEFT_OVERS ${leftOverClasses.size()} ${leftOverClasses.join(", ")}")
            } else {
                log.info("All fine: all classes are covered in architecture definition.")
            }
        } else {
            log.warn("Can't check for missing classes: Node {} not found.", nodeName)
        }
    }
}