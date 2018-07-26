package pts;

import java.lang.reflect.Parameter;
import java.util.*;

import org.jboss.util.NotImplementedException;
import polyglot.ext.param.types.Param;
import soot.Local;
import soot.SootMethod;
import soot.Value;
import soot.jimple.ArrayRef;
import soot.jimple.InstanceFieldRef;
import soot.jimple.InvokeExpr;
import soot.jimple.ParameterRef;
import soot.jimple.Ref;
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

class Ref2RefAssign {
	//
    Ref from, to;
    Ref2RefAssign(Ref from, Ref to) {
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

class ArrayConstraint {
	int allocIdOfContent;
	ArrayConstraint(int allocId) {
		this.allocIdOfContent = allocId;
	}
}

/**
 * One anderson represents a method.
 */
public class Anderson {
    public static final Map<SootMethod, Anderson> pool = new HashMap<>();

    public final Map<Integer, Local> queries = new TreeMap<>();

    // Temporary value to record the current method.
    private SootMethod currentMethod = null;

    /**
     * This reference describes under which call site,
     * the current method is invoked.
     * It is used to get argument references, which are used to
     * PTS that the parameters need.
     */
    private InvokeExpr currentCallSite = null;
    private List<Value> returnList = new ArrayList<>();

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

	private List<ArrayConstraint> arrayConstraints = new ArrayList<>();
	void addArrayConstraint(int alloc) {
		arrayConstraints.add(new ArrayConstraint(alloc));
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

    private Map<Integer, Set<Integer>> arrayContentPTS = new HashMap<>();

    /**
     * Only the PTS of return value.
     * TODO how to describe side effects?
     */
    private Map<InvokeExpr, Set<Integer>> invokePTS = new HashMap<>();
    private Map<InvokeExpr, SootMethod> callers = new HashMap<>();
    private Map<InvokeExpr, Map<Local, Set<Integer>>> localPtsBySite = new HashMap<>();

    public final List<Anderson> calleeList = new ArrayList<>();

    private boolean enable = true;

    public boolean enabled() { return enable; }

    private DebugLogger dl = new DebugLogger();

    public void run() {
        enable = false;

        // For entry main
        if (invokePTS.keySet().isEmpty()) {
            runUnderCallSite();
            return;
        }

        for (InvokeExpr callSite : invokePTS.keySet()) {
            // Env setup
            this.currentCallSite = callSite;

            // Exec
            boolean update = runUnderCallSite();

            if (update) {
                // Enable all callee.
                for (Anderson pass : calleeList) {
                    pass.wakeup();
                }
            }
            // TODO Notify assignment.
        }
    }

    private boolean runReturn() {
        for (Value rtn : returnList) {
            if (rtn instanceof Local) {
                Local local = (Local) rtn;
                Set<Integer> pts = localPTS.get(local);
                if (!invokePTS.containsKey(currentCallSite)) {
                    invokePTS.put(currentCallSite, new TreeSet<>());
                }
                boolean update = invokePTS.get(currentCallSite).addAll(pts);
                if (update) {
                    Anderson.pool.get(callers.get(currentCallSite)).wakeup();
                }
            }
            else {
                dl.log(true, "Unkonw return value type " + rtn.getClass());
            }
        }

        // Return does not interfere the analysis inside the method,
        // it is only used to wake up callers.
        return false;
    }

    protected void wakeup() {
        enable = true;
    }

    private boolean runUnderCallSite() {
        dl.log(dl.debug_all, "Anderson on " + currentMethod.getName());

        if (!localPtsBySite.containsKey(currentCallSite)) {
            localPtsBySite.put(currentCallSite, new HashMap<>());
        }

        localPTS = localPtsBySite.get(currentCallSite);

        for (NewConstraint nc : newConstraints) {
            if (!localPTS.containsKey(nc.to)) {
                localPTS.put(nc.to, new TreeSet<>());
            }
            if (nc.allocId > 0) {
                dl.log(dl.typePrint, "to class: %s", nc.to.getClass().getSimpleName());
                dl.log(dl.intraProc, "Mark %s -> %d (Alloc)", nc.to.getName(), nc.allocId);
            }
            localPTS.get(nc.to).add(nc.allocId);
        }
        int round = 0;
        boolean global_update = false;
		for (boolean flag = true; flag; ) {
		    dl.log(dl.intraProc, "Round = %d ----------------------", round);
            flag = false;
            flag |= runParamAssign();
		    flag |= runL2LAssign();
            flag |= runL2RAssign();
            flag |= runR2LAssign();
            flag |= runPhi2LocalAssign();
            flag |= runInvoke2Local();
            flag |= runReturn();
            dl.log(dl.intraProc, "Flag = %b", flag);
            global_update |= flag;
            round += 1;
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

            if (!localPTS.containsKey(dst)) {
                localPTS.put(dst, new TreeSet<>());
            }

            int index = src.getIndex();
            Value arg = currentCallSite.getArg(index);
            if (arg instanceof Local) {
                Set<Integer> pts = pool.get(callers.get(currentCallSite))
                                       .getMergedLocalPTS()
                                       .getOrDefault(arg, new TreeSet<>());
                update |= localPTS.get(dst).addAll(pts);
                dl.log(dl.param, "Update local %s's PTS from callsite local %s to %s",
                        dst.getName(), ((Local) arg).getName(), localPTS.get(dst));
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

	        if (!localPTS.containsKey(dst)) {
	            localPTS.put(dst, new TreeSet<>());
            }

            Set<Integer> pts = Anderson.pool.get(src.getMethod()).invokePTS.getOrDefault(src, new TreeSet<>());
            update |= localPTS.get(dst).addAll(pts);
        }
        return update;
    }

    private boolean runPhi2LocalAssign() {
	    boolean flag = false;
	    for (Pair<PhiExpr, Local> pair : phi2Local) {
	        PhiExpr phi = pair.getO1();
	        Local dst = pair.getO2();

	        // Init destination Local's PTS
            if (!localPTS.containsKey(dst)) {
                localPTS.put(dst, new TreeSet<>());
            }

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

                flag |= localPTS.get(dst).addAll(localPTS.get(local));
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
        dl.log(dl.fieldSensitive,"reach here, l2ra  1!\n");
        boolean flag = false;
        for (Local2RefAssign l2ra: local2RefAssigns) {
            dl.log(dl.fieldSensitive,"reach here, l2ra  2!\n");
            dl.log(dl.constraintPrint, "%s = %s\n", l2ra.to, l2ra.from);
            dl.log(dl.fieldSensitive, "%s = %s\n", l2ra.to.getClass(), l2ra.from);
            // if from is empty, skip
            if (!localPTS.containsKey(l2ra.from)) {
                continue;
            }
            dl.log(dl.fieldSensitive,"reach here, l2ra  after !\n");
            if (l2ra.to instanceof ArrayRef) {
                dl.log(dl.fieldSensitive,"reach here, l2ra  ArrayRef!\n");
                ArrayRef ar = (ArrayRef) l2ra.to;

                if (baseInLocalPTS(ar)) {
                    Local base = (Local) ar.getBase();
                    for (Integer i: localPTS.get(base)) {
                        // check to, init it if empty
                        if (!arrayContentPTS.containsKey(i)) {
                            arrayContentPTS.put(i, new TreeSet<>());
                        }

                        for (Integer pointee: localPTS.get(l2ra.from)) {
                            if (arrayContentPTS.get(i).contains(pointee)) {
                                continue;
                            }
                            dl.log(dl.intraProc && pointee > 0, "Mark %d -> %d",
                                    i, pointee);
                        }

                        flag |= arrayContentPTS.get(i).addAll(localPTS.get(l2ra.from));
                    }

                } else {
                    dl.loge(dl.intraProc, "base of array ref is not local!");
                }

            } else if (l2ra.to instanceof InstanceFieldRef) {
                dl.log(dl.fieldSensitive,"reach here, l2ra instancefieldref!\n");
                InstanceFieldRef ifr = (InstanceFieldRef) l2ra.to;

                if (baseInLocalPTS(ifr)) {
                    Local base = (Local) ifr.getBase();
                    dl.log(dl.fieldSensitive,base.toString()+"=====================\n");
                    for (Integer i: localPTS.get(base)) {
                        // check to, init it if empty
                        if (!arrayContentPTS.containsKey(i)) {
                            arrayContentPTS.put(i, new TreeSet<>());
                        }

                        for (Integer pointee: localPTS.get(l2ra.from)) {
                            if (arrayContentPTS.get(i).contains(pointee)) {
                                continue;
                            }
                            dl.log(dl.intraProc && pointee > 0, "Mark %d -> %d\n",
                                    i, pointee);
                        }

                        flag |= arrayContentPTS.get(i).addAll(localPTS.get(l2ra.from));
                    }

                } else {
                    dl.loge(dl.intraProc, "base of array ref is not local!\n");
                }

            }
            else {
                dl.loge(dl.fieldSensitive, "Ref type not implemented!\n");
                throw new NotImplementedException();
            }
        }
        return flag;
    }

    private boolean runR2LAssign() {
        boolean flag = false;
        for (Ref2LocalAssign r2la: ref2LocalAssigns) {
            dl.log(dl.constraintPrint, "%s = %s", r2la.to, r2la.from);

            // check to, and init it if empty
            if (!localPTS.containsKey(r2la.to)) {
                localPTS.put(r2la.to, new TreeSet<>());
                //					dl.log(dl.intraProc,"Add Pointer: %s\n", ac.to.getName());
            }

            // if from is empty, skip
            if (r2la.from instanceof ArrayRef) {
                ArrayRef ar = (ArrayRef) r2la.from;
                if (!baseInLocalPTS(ar)) {
                    dl.loge(dl.intraProc, "base of array ref is not local or not in local PTS!");
                    continue;
                }
                boolean empty = true;
                Local base = (Local) ar.getBase();
                for (Integer i: localPTS.get(base)) {
                    if (arrayContentPTS.containsKey(i) &&
                            !arrayContentPTS.get(i).isEmpty()) {
                        empty = false;
                    }
                }
                if (empty) {
                    dl.loge(dl.intraProc, "point set of content of array not found");
                    continue;
                }

            } else if (r2la.from instanceof InstanceFieldRef) {
//                dl.log(dl.fieldSensitive,"reach here, r2la instancefieldref first!\n");
                InstanceFieldRef ifr = (InstanceFieldRef) r2la.from;
                if (!baseInLocalPTS(ifr)) {
                    dl.loge(dl.intraProc, "base of array ref is not local or" +
                            " not in local PTS!\n");
                    continue;
                }
                boolean empty = true;
                Local base = (Local) ifr.getBase();
                dl.log(dl.fieldSensitive," r2l field base is : %s, localPTS.get(base) is: %s\n", base, localPTS.get(base).toString());
                for (Integer i: localPTS.get(base)) {
                    //dl.log(dl.fieldSensitive, "containsKey is %b, is Empty is %b\n", arrayContentPTS.containsKey(i), arrayContentPTS.get(i).isEmpty());
                    if (arrayContentPTS.containsKey(i) &&
                            !arrayContentPTS.get(i).isEmpty()) {
                        empty = false;
                    }
                }
                if (empty) {
                    dl.loge(dl.intraProc, "point set of content of array not found field\n");
                    continue;
                }
            } else {
                dl.loge(dl.intraProc, "Ref type not implemented!");
                throw new NotImplementedException();
            }

            // check to, and init it if empty
            if (!localPTS.containsKey(r2la.to)) {
                localPTS.put(r2la.to, new TreeSet<>());
            }

            Local base;
            if (r2la.from instanceof ArrayRef)
            {
                ArrayRef ar = (ArrayRef) r2la.from;
                base = (Local) ar.getBase();
            } else if (r2la.from instanceof InstanceFieldRef) {
                InstanceFieldRef ifr = (InstanceFieldRef) r2la.from;
                base = (Local) ifr.getBase();
            } else {
                base = null;
            }

            for (Integer i: localPTS.get(base)) {
                if (arrayContentPTS.containsKey(i)) {
                    for (Integer pointee: arrayContentPTS.get(i)) {
                        if (localPTS.get(r2la.to).contains(pointee)) {
                            continue;
                        }
                        dl.log(dl.intraProc && pointee > 0, "Mark %s -> %d",
                                r2la.to.getName(), pointee);
                    }

                    flag |= localPTS.get(r2la.to).addAll(arrayContentPTS.get(i));
                }
            }
        }
        return flag;
    }

	private boolean runL2LAssign() {
        boolean flag = false;
        for (Local2LocalAssign ac : local2LocalAssigns) {
            dl.log(dl.constraintPrint, "%s = %s\n", ac.to, ac.from);

            // check to, and init it if empty
            if (!localPTS.containsKey(ac.to)) {
                localPTS.put(ac.to, new TreeSet<>());
                //					dl.log(dl.intraProc,"Add Pointer: %s\n", ac.to.getName());
            }

            // if from is empty, skip
            if (!localPTS.containsKey(ac.from)) {
                continue;
            }

            // check to, and init it if empty
            if (!localPTS.containsKey(ac.to)) {
                localPTS.put(ac.to, new TreeSet<>());
            }

            for (Integer pointee: localPTS.get(ac.from)) {
                if (localPTS.get(ac.to).contains(pointee)) {
                    continue;
                }
                dl.log(dl.intraProc && pointee > 0,"Mark %s -> %d", ac.to.getName(), pointee);
            }
            if (localPTS.get(ac.to).addAll(localPTS.get(ac.from))) {
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
    public Map<Local, Set<Integer>> getMergedLocalPTS() {
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
}
