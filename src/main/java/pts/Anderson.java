package pts;

import java.util.*;

import org.jboss.util.NotImplementedException;
import soot.Local;
import soot.SootField;
import soot.SootMethod;
import soot.Value;
import soot.jimple.*;
import soot.shimple.PhiExpr;
import soot.toolkits.scalar.Pair;
import soot.toolkits.scalar.ValueUnitPair;

class Local2LocalAssign {
	Local from, to;
	Local2LocalAssign(Local from, Local to) {
		this.from = from;
		this.to = to;
	}
}

class Ref2LocalAssign {
	// x = a[2]
    Local to;
    Ref from;
    Ref2LocalAssign(Ref from, Local to) {
        this.to = to;
		this.from = from;
	}
}

class Local2RefAssign {
	//
    Local from;
    Ref to;
    Local2RefAssign(Local from, Ref to) {
        this.to = to;
		this.from = from;
	}
}

class NewConstraint {
	Local to;
	int allocId;
	NewConstraint(int allocId, Local to) {
		this.allocId = allocId;
		this.to = to;
	}
}


/**
 * One anderson represents a method.
 */
public class Anderson {
    public static final Map<InvokeExpr, MemEnv> expr2EnvIn = new HashMap<>();
    public static final Map<InvokeExpr, MemEnv> expr2EnvOut = new HashMap<>();
    public static final Map<Ref, MemEnv> ref2EnvIn = new HashMap<>();
    public static final Map<Ref, MemEnv> ref2EnvOut = new HashMap<>();
    public static final Map<Value, MemEnv> rtn2EnvIn = new HashMap<>();
    public Set<MemEnv> voidReturnEnvIn = new HashSet<>();
    public final List<MemEnv> memEnvList = new ArrayList<>();
    public MemEnv entryEnv;

    private static final Set<Integer> Empty = new TreeSet<>();

    public static final Map<SootMethod, Anderson> pool = new HashMap<>();

    public final Map<Integer, Local> queries = new TreeMap<>();

    // Temporary value to record the current method.
    private SootMethod currentMethod;

    /**
     * This reference describes under which call site,
     * the current method is invoked.
     * It is used to get argument references, which are used to
     * PTS that the parameters need.
     */
    private InvokeExpr currentCallSite = null;
    private List<Value> returnList = new ArrayList<>();
    private Local currentObject;

    public Anderson(SootMethod method) {
        currentMethod = method;
    }

    public void addCallSite(InvokeExpr invoke, SootMethod caller) {
        invokePTS.put(invoke, new TreeSet<>());
        callers.put(invoke, caller);
    }

	private List<Local2LocalAssign> local2LocalAssigns = new ArrayList<>();

    void addAssignConstraint(Local from, Local to) {
		local2LocalAssigns.add(new Local2LocalAssign(from, to));
	}

	private List<NewConstraint> newConstraints = new ArrayList<>();
	void addNewConstraint(int alloc, Local to) {
		newConstraints.add(new NewConstraint(alloc, to));
	}

	private List<Ref2LocalAssign> ref2LocalAssigns = new ArrayList<>();
	void addRef2LocalAssign(Ref from, Local to) {
		ref2LocalAssigns.add(new Ref2LocalAssign(from, to));
	}

	private List<Local2RefAssign> local2RefAssigns = new ArrayList<>();
	void addLocal2RefAssign(Local from, Ref to) {
		local2RefAssigns.add(new Local2RefAssign(from, to));
	}

	private List<Pair<PhiExpr, Local>> phi2Local = new ArrayList<>();
    public void addPhi2Local(PhiExpr phi, Local lop) {
        phi2Local.add(new Pair<>(phi, lop));
    }

    private List<Pair<InvokeExpr, Local>> invoke2Local = new ArrayList<>();
    public void addInvoke2Local(InvokeExpr src, Local dst) {
        invoke2Local.add(new Pair<>(src, dst));
    }

    private List<Pair<ParameterRef, Local>> param2Local = new ArrayList<>();

	private Map<Local, Set<Integer>> localPTS = null;

    // public final static Map<Integer, Set<Integer>> arrayContentPTS = new HashMap<>();

    /**
     * Only the PTS of return value.
     */
    private Map<InvokeExpr, Set<Integer>> invokePTS = new HashMap<>();

    /**
     * The caller of a given call site.
     */
    private Map<InvokeExpr, SootMethod> callers = new HashMap<>();

    /**
     * Local environment per call site.
     */
    private Map<InvokeExpr, Map<Local, Set<Integer>>> localPtsBySite = new HashMap<>();

    /**
     * Callee list.
     */
    public final List<Anderson> calleeList = new ArrayList<>();

    /**
     * Whether this anderson should run (because related environment is updated).
     */
    private boolean enable = true;

    public boolean enabled() { return enable; }

    private DebugLogger dl = new DebugLogger();

    public boolean run() {
        enable = false;

        // For entry main
        if (invokePTS.keySet().isEmpty()) {
            return runUnderCallSite();
        }

        boolean update = false;
        for (InvokeExpr callSite : invokePTS.keySet()) {
            // Env setup
            this.currentCallSite = callSite;
            this.currentObject = null;
            if (callSite instanceof SpecialInvokeExpr) {
                this.currentObject = (Local)((SpecialInvokeExpr) callSite).getBase();
            }
            else if (callSite instanceof VirtualInvokeExpr) {
                this.currentObject = (Local)((VirtualInvokeExpr) callSite).getBase();
            }

            // Exec
            update |= runUnderCallSite();

            if (update) {
                // Enable all callee.
                dl.log(dl.interProc, "wake up callees");
                for (Anderson pass : calleeList) {
                    pass.wakeup();
                }
            }
        }

        return update;
    }

    private boolean runReturn() {
        boolean updated = false;
        for (Value rtn : returnList) {
            if (rtn instanceof Local) {
                Local local = (Local) rtn;
                dl.log(dl.debug_all, "return local is " + local.getName());
                Set<Integer> pts = getLocalPTS(local);
                dl.log(dl.debug_all, "return local pts is " + pts);
                updated |= invokePTS.get(currentCallSite).addAll(pts);
                updated |= expr2EnvOut.get(currentCallSite).mergeEnv(rtn2EnvIn.get(rtn));
            }
            else {
                dl.log(true, "Unkonw return value type " + rtn.getClass());
            }
        }

        if (currentCallSite != null) {
            for (MemEnv env : voidReturnEnvIn) {
                updated |= expr2EnvOut.get(currentCallSite).mergeEnv(env);
            }
        }

        // Return does not interfere the analysis inside the method,
        // it is only used to wake up callers.
        return updated;
    }

    private void wakeup() {
        enable = true;
    }

    private boolean runUnderCallSite() {
        dl.log(dl.debug_all, "\nAnderson on " + currentMethod);

        if (currentCallSite != null) {
            MemEnv in = expr2EnvIn.get(currentCallSite);
            entryEnv.replace(in);
        }
        System.out.println("EnvIn: " + entryEnv);
        if (currentCallSite != null) {
            System.out.println("Before:: EnvOut: " + expr2EnvOut.get(currentCallSite));
        }
        memEnvClosure();

        if (!localPtsBySite.containsKey(currentCallSite)) {
            localPtsBySite.put(currentCallSite, new HashMap<>());
        }

        localPTS = localPtsBySite.get(currentCallSite);

        dl.log(dl.debug_all, "before localPTS: " + localPTS);
        // dl.log(dl.debug_all, "before refPTS: " + arrayContentPTS);

        for (NewConstraint nc : newConstraints) {
            if (!localPTS.containsKey(nc.to)) {
                localPTS.put(nc.to, new TreeSet<>());
            }
            if (true) {
                dl.log(dl.typePrint, "to class: %s", nc.to.getClass().getSimpleName());
                dl.log(dl.intraProc, "Mark %s -> %d (Alloc)", nc.to.getName(), nc.allocId);
                getLocalPTS(nc.to).add(nc.allocId);
            }
        }

        int round = 0;
        boolean global_update = false;
		for (boolean flag = true; flag; ) {
		    dl.log(dl.intraProc, "Round = %d ----------------------", round);
            flag = runThisAssign();
            flag |= runParamAssign();
            flag |= runL2LAssign();
            flag |= runL2RAssign();
            flag |= runR2LAssign();
            flag |= runPhi2LocalAssign();
            flag |= runInvoke2Local();
            dl.log(dl.intraProc, "Flag = %b", flag);
            global_update |= flag;
            round += 1;
		}

		global_update |= memEnvClosure();
		global_update |= runReturn();

        dl.log(dl.debug_all, "after localPTS: " + localPTS);
        // dl.log(dl.debug_all, "after refPTS: " + arrayContentPTS);

        if (expr2EnvOut.containsKey(currentCallSite)) {
            System.out.println("EnvOut: " + expr2EnvOut.get(currentCallSite));
        }

		return global_update;
	}

    private boolean runParamAssign() {
        if (currentCallSite == null) {
            return false;
        }

        boolean update = false;

        for (Pair<ParameterRef, Local> pair : param2Local) {
            ParameterRef src = pair.getO1();
            Local dst = pair.getO2();

            tryInitLocal(dst);

            int index = src.getIndex();
            Value arg = currentCallSite.getArg(index);
            if (arg instanceof Local) {
                dl.log(dl.param, "[PARAM] local %s pts %s", dst.getName(), getLocalPTS(dst));
                Set<Integer> pts = pool.get(callers.get(currentCallSite))
                                       .getMergedLocalPTS()
                                       .getOrDefault(arg, Empty);
                dl.log(dl.param, "[PARAM] arg %s pts %s", ((Local)arg).getName(), pts);
                update |= getLocalPTS(dst).addAll(pts);
            }
            else {
                dl.log(dl.param, "Unknown arg type:: " + arg.getClass());
            }
        }

        return update;
    }

    private boolean runInvoke2Local() {
	    boolean update = false;
	    for (Pair<InvokeExpr, Local> pair : invoke2Local) {
	        InvokeExpr src = pair.getO1();
	        Local dst = pair.getO2();
            dl.log(dl.interProc, "invoke assign: " + dst + " <- " + src);

            tryInitLocal(dst);

            Set<Integer> pts = Anderson.pool.get(src.getMethod()).getInvokePTS(src);
            update |= getLocalPTS(dst).addAll(pts);
        }
        return update;
    }

    private boolean runPhi2LocalAssign() {
	    boolean flag = false;
	    for (Pair<PhiExpr, Local> pair : phi2Local) {
	        PhiExpr phi = pair.getO1();
	        Local dst = pair.getO2();

	        // Init destination Local's PTS
            tryInitLocal(dst);

            // Merge each phi's source into destination
	        for (ValueUnitPair val : phi.getArgs()) {
	            Value v = val.getValue();
	            if (!(v instanceof Local)) {
	                dl.log(dl.debug_all, "(discard) phi value type is " + v.getClass());
	                continue;
	            }
                Local local = (Local) v;
	            if (!localPTS.containsKey(local)) {
	                dl.log(dl.debug_all, "phi source " + local.getName() + " does not have PTS");
	                continue;
                }

                flag |= getLocalPTS(dst).addAll(getLocalPTS(local));
            }
        }
        return flag;
	}

    private boolean baseInLocalPTS(ArrayRef ar) {
        if (ar.getBase() instanceof Local) {
            Local base = (Local) ar.getBase();
            return localPTS.containsKey(base);
        }
        return false;
    }

    private boolean baseInLocalPTS(InstanceFieldRef ifr) {
        if (ifr.getBase() instanceof Local) {
            Local base = (Local) ifr.getBase();
            return localPTS.containsKey(base);
        }
        return false;
    }

    private boolean runL2RAssign() {
        boolean flag = false;
        for (Local2RefAssign l2ra: local2RefAssigns) {
            dl.log(dl.constraintPrint, "%s = %s", l2ra.to, l2ra.from);
            dl.log(dl.fieldSensitive, "%s = %s", l2ra.to.getClass(), l2ra.from);
            // if from is empty, skip
            if (!localPTS.containsKey(l2ra.from)) {
                continue;
            }
            dl.log(dl.fieldSensitive,"reach here, l2ra  after !");
            if (l2ra.to instanceof ArrayRef) {
                dl.log(dl.fieldSensitive,"reach here, l2ra  ArrayRef!");
                ArrayRef ar = (ArrayRef) l2ra.to;

                if (baseInLocalPTS(ar)) {
                    Local base = (Local) ar.getBase();
                    MemEnv env = ref2EnvOut.get(ar);
                    for (Integer i: getLocalPTS(base)) {
                        flag |= env.addPTS(i, null, getLocalPTS(l2ra.from));
                    }
                    dl.log(dl.intraProc, env.toString());

                } else {
                    dl.loge(dl.intraProc, "base of array ref is not local!");
                }

            }
            else if (l2ra.to instanceof InstanceFieldRef) {
                dl.log(dl.fieldSensitive,"reach here, l2ra instancefieldref!");
                InstanceFieldRef ifr = (InstanceFieldRef) l2ra.to;

                if (baseInLocalPTS(ifr)) {
                    MemEnv env = ref2EnvOut.get(ifr);
                    Local base = (Local) ifr.getBase();
                    dl.log(dl.fieldSensitive,"=====env is " + env.toString());
                    for (Integer i: getLocalPTS(base)) {
                        dl.log(dl.fieldSensitive, "i is :" + i + "field is : " + ifr.getField().getName()+ " local pts is + :"+getLocalPTS(l2ra.from));
                        flag |= env.addPTS(i, ifr.getField().getName(), getLocalPTS(l2ra.from));
                    }
                }
                else {
                    dl.loge(dl.intraProc, "base of array ref is not local!");
                }

            }
            else {
                dl.loge(dl.fieldSensitive, "Ref type not implemented!");
                throw new NotImplementedException();
            }
        }

        return flag;
    }

    private boolean runR2LAssign() {
        boolean flag = false;
        for (Ref2LocalAssign r2la: ref2LocalAssigns) {
            dl.log(dl.constraintPrint, "%s = %s", r2la.to, r2la.from);

            /*
            // if from is empty, skip
            if (r2la.from instanceof ArrayRef) {
                ArrayRef ar = (ArrayRef) r2la.from;
                if (!baseInLocalPTS(ar)) {
                    dl.loge(dl.intraProc, "base of array ref is not local or not in local PTS!");
                    continue;
                }
                boolean empty = true;
                Local base = (Local) ar.getBase();

                for (Integer i: getLocalPTS(base)) {
                    if (arrayContentPTS.containsKey(i) &&
                            !arrayContentPTS.get(i).isEmpty()) {
                        empty = false;
                    }
                }
                if (empty) {
                    dl.loge(dl.intraProc, "point set of content of array not found");
                    continue;
                }

            }
            else if (r2la.from instanceof InstanceFieldRef) {
                InstanceFieldRef ifr = (InstanceFieldRef) r2la.from;
                if (!baseInLocalPTS(ifr)) {
                    dl.loge(dl.intraProc, "base of instance ref is not local or not in local PTS!");
                    continue;
                }

                boolean empty = true;
                Local base = (Local) ifr.getBase();
                dl.log(dl.fieldSensitive," r2l field base is : %s, getLocalPTS(base) is: %s", base, getLocalPTS(base).toString());
                for (Integer i: getLocalPTS(base)) {
                    if (arrayContentPTS.containsKey(i) &&
                            !arrayContentPTS.get(i).isEmpty()) {
                        empty = false;
                    }
                }
                if (empty) {
                    dl.loge(dl.intraProc, "point set of content of array not found field");
                    continue;
                }

            }
            else {
                dl.loge(dl.intraProc, "Ref type not implemented!");
                throw new NotImplementedException();
            }
            */

            // check to, and init it if empty
            tryInitLocal(r2la.to);

            Local base;
            String field = "";
            if (r2la.from instanceof ArrayRef) {
                ArrayRef ar = (ArrayRef) r2la.from;
                base = (Local) ar.getBase();
            }
            else if (r2la.from instanceof InstanceFieldRef) {
                InstanceFieldRef ifr = (InstanceFieldRef) r2la.from;
                base = (Local) ifr.getBase();
                field = ifr.getField().getName();
            }
            else {
                base = null;
            }

            MemEnv env = ref2EnvIn.get(r2la.from);
            for (Integer i: getLocalPTS(base)) {
                for (Integer pointee: env.getPTS(i, field)) {
                    if (getLocalPTS(r2la.to).contains(pointee)) {
                        continue;
                    }
                    dl.log(dl.intraProc && pointee > 0, "Mark %s -> %d", r2la.to.getName(), pointee);
                }

                flag |= getLocalPTS(r2la.to).addAll(env.getPTS(i, field));
                System.out.println("localPTS: " + localPTS);
            }
        }
        return flag;
    }

	private boolean runL2LAssign() {
        boolean flag = false;
        for (Local2LocalAssign ac : local2LocalAssigns) {
            dl.log(dl.constraintPrint, "%s = %s", ac.to, ac.from);

            // check to, and init it if empty
            tryInitLocal(ac.to);

            // if from is empty, skip
            if (!localPTS.containsKey(ac.from)) {
                continue;
            }

            for (Integer pointee: getLocalPTS(ac.from)) {
                if (getLocalPTS(ac.to).contains(pointee)) {
                    continue;
                }
                dl.log(dl.intraProc && pointee > 0,"Mark %s -> %d", ac.to.getName(), pointee);
            }
            if (getLocalPTS(ac.to).addAll(getLocalPTS(ac.from))) {
                flag = true;
            }
        }
        return flag;
    }

	Set<Integer> getPointsToSet(Local local) {
		return getMergedLocalPTS().get(local);
	}

    public void addReturn2RefAssign(InvokeExpr invoke, Ref lop) {
    }

    public void addPhi2Ref(PhiExpr phi, Ref lop) {
    }

    public void addParamAssign(ParameterRef param, Local leftOp) {
        param2Local.add(new Pair<>(param, leftOp));
    }

    /**
     * TODO This is a trade off as we do not record the call stack
     */
    private Map<Local, Set<Integer>> getMergedLocalPTS() {
        Map<Local, Set<Integer>> pts = new HashMap<>();
        for (Map<Local, Set<Integer>> v : localPtsBySite.values()) {
            for (Map.Entry<Local, Set<Integer>> e : v.entrySet()) {
                pts.merge(e.getKey(), e.getValue(), (o, n) -> {
                    Set<Integer> r = new TreeSet<>(o);
                    r.addAll(n);
                    return r;
                });
            }
        }

        return pts;
    }

    public void addReturn(Local local) {
        this.returnList.add(local);
    }

    private List<Pair<ThisRef, Local>> thisAssign = new ArrayList<>();

    public void addThisAssign(ThisRef thisRef, Local leftOp) {
        thisAssign.add(new Pair<>(thisRef, leftOp));
    }

    private void tryInitLocal(Local local)
    {
        if (!localPTS.containsKey(local)) {
            localPTS.put(local, new TreeSet<>());
        }
    }

    private Map<Local, Set<Integer>> getCallerPTS()
    {
        return pool.get(callers.get(currentCallSite)).getMergedLocalPTS();
    }

    private boolean runThisAssign()
    {
        boolean update = false;
        for (Pair<ThisRef, Local> assign : thisAssign) {
            Local local = assign.getO2();
            tryInitLocal(local);
            if (currentObject == null) {
                dl.log(dl.debug_all, "Shit, do not have object!");
            }
            update |= getLocalPTS(local).addAll(getCallerPTS().getOrDefault(currentObject, Empty));
        }
        return update;
    }

    private boolean memEnvClosure()
    {
        boolean globalUpdated = false;
        boolean updated = true;

        System.out.println("Start mem env closure");
        while (updated) {
            updated = false;
            for (MemEnv env : memEnvList) {
                updated |= env.update();
            }
            globalUpdated |= updated;
        }
        System.out.println("End mem env closure");

        return globalUpdated;
    }

    private Set<Integer> getLocalPTS(Local local)
    {
        tryInitLocal(local);
        return localPTS.get(local);
    }

    private Set<Integer> getInvokePTS(InvokeExpr i)
    {
        if (!invokePTS.containsKey(i)) {
            invokePTS.put(i, new TreeSet<>());
        }
        return invokePTS.get(i);
    }
}
