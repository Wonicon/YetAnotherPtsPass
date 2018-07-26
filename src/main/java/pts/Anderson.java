package pts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.jboss.util.NotImplementedException;
import soot.Local;
import soot.jimple.ArrayRef;
import soot.jimple.InstanceFieldRef;
import soot.jimple.Ref;

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

public class Anderson {

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

	private Map<Local, TreeSet<Integer>> localPTS = new HashMap<>();

    private Map<Integer, TreeSet<Integer>> arrayContentPTS = new HashMap<>();

    private DebugLogger dl = new DebugLogger();

	void run() {
		for (NewConstraint nc : newConstraints) {
            if (!localPTS.containsKey(nc.to)) {
                localPTS.put(nc.to, new TreeSet<>());

            }
            if (nc.allocId > 0) {
                dl.log(dl.typePrint, "to class: %s\n", nc.to.getClass().getSimpleName());
                dl.log(dl.intraProc, "Mark %s -> %d (Alloc)\n", nc.to.getName(), nc.allocId);
            }
            localPTS.get(nc.to).add(nc.allocId);
        }
        int round = 0;
		for (boolean flag = true; flag; ) {
		    dl.log(dl.intraProc, "Round = %d ----------------------\n", round);
            flag = runL2LAssign();
            flag |= runL2RAssign();
            flag |= runR2LAssign();
            dl.log(dl.intraProc, "Flag = %b\n", flag);
            round += 1;
		}
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
                            dl.log(dl.intraProc && pointee > 0, "Mark %d -> %d\n",
                                    i, pointee);
                        }

                        flag |= arrayContentPTS.get(i).addAll(localPTS.get(l2ra.from));
                    }

                } else {
                    dl.loge(dl.intraProc, "base of array ref is not local!\n");
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
            dl.log(dl.constraintPrint, "%s = %s\n", r2la.to, r2la.from);

            // check to, and init it if empty
            if (!localPTS.containsKey(r2la.to)) {
                localPTS.put(r2la.to, new TreeSet<>());
                //					dl.log(dl.intraProc,"Add Pointer: %s\n", ac.to.getName());
            }

            // if from is empty, skip
            if (r2la.from instanceof ArrayRef) {
                ArrayRef ar = (ArrayRef) r2la.from;
                if (!baseInLocalPTS(ar)) {
                    dl.loge(dl.intraProc, "base of array ref is not local or" +
                            " not in local PTS!\n");
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
                    dl.loge(dl.intraProc, "point set of content of array not found array\n");
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
                dl.loge(dl.intraProc, "Ref type not implemented!\n");
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
                        dl.log(dl.intraProc && pointee > 0, "Mark %s -> %d\n",
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



            for (Integer pointee: localPTS.get(ac.from)) {
                if (localPTS.get(ac.to).contains(pointee)) {
                    continue;
                }
                dl.log(dl.intraProc && pointee > 0,"Mark %s -> %d\n", ac.to.getName(), pointee);
            }
            if (localPTS.get(ac.to).addAll(localPTS.get(ac.from))) {
                flag = true;
            }
        }
        return flag;
    }

	TreeSet<Integer> getPointsToSet(Local local) {
		return localPTS.get(local);
	}
}
