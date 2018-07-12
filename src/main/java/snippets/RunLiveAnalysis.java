package snippets;

import soot.*;
import soot.jbco.util.SimpleExceptionalGraph;
import soot.options.ShimpleOptions;
import soot.shimple.Shimple;
import soot.shimple.ShimpleBody;
import soot.toolkits.graph.*;
import soot.toolkits.scalar.*;
import java.util.*;

public class RunLiveAnalysis
{
    public static void main(String[] args) {
        args = new String[] {"Foo"};

        Scene.v().setSootClassPath(".:rt.jar:jce.jar");
        SootClass sClass = Scene.v().loadClassAndSupport(args[0]);
        Scene.v().loadNecessaryClasses();
        sClass.setApplicationClass();

        for (SootMethod m : sClass.getMethods()) {
            // Body body = m.retrieveActiveBody();
            ShimpleBody body = Shimple.v().newBody(m.retrieveActiveBody());
            System.out.println("=======================================");
            System.out.println(m.getName());
            BlockGraph bb = new ExceptionalBlockGraph(body);
            for (Block blk : bb.getBlocks()) {
                for (Unit unit : bb.getBody().getUnits()) {
                    System.out.println(unit);
                }
            }
        }
    }
}
