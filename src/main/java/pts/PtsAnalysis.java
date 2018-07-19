package pts;

import soot.Local;
import soot.Unit;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.scalar.ArraySparseSet;
import soot.toolkits.scalar.FlowSet;
import soot.toolkits.scalar.ForwardFlowAnalysis;

public class PtsAnalysis extends ForwardFlowAnalysis {
    private FlowSet emptySet;

    public PtsAnalysis(DirectedGraph g) {
        super(g);
        emptySet = new ArraySparseSet();
        doAnalysis();
    }

    @Override
    protected void merge(Object in1, Object in2, Object out) {

    }

    @Override
    protected void copy(Object src, Object dst) {
        ((FlowSet) src).copy((FlowSet) dst);
    }

    @Override
    protected Object newInitialFlow() {
        return emptySet.clone();
    }

    @Override
    protected Object entryInitialFlow() {
        // TODO Add argument pointsToSet as initial value.
        return emptySet.clone();
    }

    @Override
    protected void flowThrough(Object in, Object node, Object out) {

    }

    private void kill(FlowSet in, Unit u, FlowSet outSet) {
        FlowSet kills = (FlowSet) emptySet.clone();
    }

    private void gen(FlowSet out, Unit u) {

    }
}
