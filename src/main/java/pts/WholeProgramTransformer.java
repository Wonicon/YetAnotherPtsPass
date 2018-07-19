package pts;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.TreeSet;

import com.sun.org.apache.xpath.internal.operations.And;
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
        Anderson anderson = new Anderson();

        ReachableMethods reachableMethods = Scene.v().getReachableMethods();
        QueueReader<MethodOrMethodContext> qr = reachableMethods.listener();

        while (qr.hasNext()) {
            SootMethod method = qr.next().method();
            if (!method.hasActiveBody()) {
                continue;
            }

            int allocId = 0;
            for (Unit u : method.getActiveBody().getUnits()) {
                if (u instanceof InvokeStmt) {
//                    System.out.println("Reached here 0");
                    InvokeExpr ie = ((InvokeStmt) u).getInvokeExpr();
                    if (ie.getMethod().toString().equals("<benchmark.internal.Benchmark: void alloc(int)>")) {
                        allocId = ((IntConstant)ie.getArgs().get(0)).value;
                    }
                    if (ie.getMethod().toString().equals("<benchmark.internal.Benchmark: void test(int,java.lang.Object)>")) {
                        allocId = ((IntConstant)ie.getArgs().get(0)).value;
                        Value v = ie.getArgs().get(1);
                        int id = ((IntConstant)ie.getArgs().get(0)).value;
                        queries.put(id, (Local)v);
                    }
                }
                if (u instanceof DefinitionStmt) {
//                    System.out.println("Reached here 3");
                    Object lop = ((DefinitionStmt)u).getLeftOp(),
                            rop = ((DefinitionStmt)u).getRightOp();
                    if (rop instanceof NewExpr) {
                        anderson.addNewConstraint(allocId, (Local)lop);
                    }
                    if (lop instanceof Local && rop instanceof Local) {
                        anderson.addAssignConstraint((Local)rop, (Local)lop);
                    }
                }
            }
        }

        anderson.run();

        // Format result
        StringBuilder answer = new StringBuilder();
        for (Entry<Integer, Local> q : queries.entrySet()) {
            TreeSet<Integer> result = anderson.getPointsToSet(q.getValue());
            answer.append(q.getKey().toString()).append(":");
            for (Integer i: result) {
                answer.append(" ").append(i.toString());
            }
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
