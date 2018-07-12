package pts;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import soot.Local;
import soot.MethodOrMethodContext;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.DefinitionStmt;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.NewExpr;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.shimple.Shimple;
import soot.shimple.ShimpleBody;
import soot.toolkits.graph.BlockGraph;
import soot.toolkits.graph.CompleteBlockGraph;
import soot.util.queue.QueueReader;

public class WholeProgramTransformer extends SceneTransformer {

    @Override
    protected void internalTransform(String arg0, Map<String, String> arg1) {

        TreeMap<Integer, Local> queries = new TreeMap<>();

        ReachableMethods reachableMethods = Scene.v().getReachableMethods();
        QueueReader<MethodOrMethodContext> qr = reachableMethods.listener();

        while (qr.hasNext()) {
            SootMethod method = qr.next().method();
            if (!method.hasActiveBody()) {
                continue;
            }

            ShimpleBody sb = Shimple.v().newBody(method.getActiveBody());
            BlockGraph g = new CompleteBlockGraph(sb);
            new PtsAnalysis(g);  // TODO manage and pass argument pts environment <func, callsite, arg, {pts}>

            // TODO Move this to flowThrough
            int allocId = 0;

            for (Unit u : method.getActiveBody().getUnits()) {
                if (u instanceof InvokeStmt) {
                    InvokeExpr ie = ((InvokeStmt) u).getInvokeExpr();
                    if (ie.getMethod().toString().equals("<benchmark.internal.Benchmark: void alloc(int)>")) {
                        allocId = ((IntConstant)ie.getArgs().get(0)).value;
                    }
                    if (ie.getMethod().toString().equals("<benchmark.internal.Benchmark: void test(int,java.lang.Object)>")) {
                        Value v = ie.getArgs().get(1);
                        int id = ((IntConstant)ie.getArgs().get(0)).value;
                        queries.put(id, (Local)v);
                    }
                }
                if (u instanceof DefinitionStmt) {
                    if (((DefinitionStmt)u).getRightOp() instanceof NewExpr) {
                        // TODO Register alloc with allocId
                    }
                    if (((DefinitionStmt)u).getLeftOp() instanceof Local && ((DefinitionStmt)u).getRightOp() instanceof Local) {
                        // TODO point set update
                    }
                }
            }
        }

        // Format result
        StringBuilder answer = new StringBuilder();
        for (Entry<Integer, Local> q : queries.entrySet()) {
            answer.append(q.getKey().toString()).append(":");
            answer.append("\n");
        }

        // Output result
        try (PrintStream ps = new PrintStream(new FileOutputStream(new File("result.txt")))) {
            ps.print(answer);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

}
