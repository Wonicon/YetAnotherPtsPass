package pts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import soot.Local;
import soot.jimple.Ref;

class AssignConstraint {
	Local from, to;
	AssignConstraint(Local from, Local to) {
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

class ArrayConstraint {
	int allocIdOfContent;
	ArrayConstraint(int allocId) {
		this.allocIdOfContent = allocId;
	}
}

public class Anderson {

	private List<AssignConstraint> assignConstraints = new ArrayList<>();
	void addAssignConstraint(Local from, Local to) {
		assignConstraints.add(new AssignConstraint(from, to));
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
	void local2RefAssign(Local from, Ref to) {
		local2RefAssigns.add(new Local2RefAssign(from, to));
	}

	private Map<Local, TreeSet<Integer>> pts = new HashMap<>();

	void run() {
		DebugLogger dl;
	    dl = new DebugLogger();

		for (NewConstraint nc : newConstraints) {
			if (!pts.containsKey(nc.to)) {
				pts.put(nc.to, new TreeSet<>());
//				dl.log(dl.intraProc,"Add AllocId: %d\n", nc.allocId);
			}
			if (nc.allocId > 0) {
				dl.log(dl.intraProc, "Mark %s -> %d (Alloc)\n", nc.to.getName(), nc.allocId);
			}
			pts.get(nc.to).add(nc.allocId);
		}
		for (boolean flag = true; flag; ) {
			flag = false;
			for (AssignConstraint ac : assignConstraints) {
				if (!pts.containsKey(ac.from)) {
					continue;
				}	
				if (!pts.containsKey(ac.to)) {
					pts.put(ac.to, new TreeSet<>());
//					dl.log(dl.intraProc,"Add Pointer: %s\n", ac.to.getName());
				}
				for (Integer pointee: pts.get(ac.from)) {
				    if (pts.get(ac.to).contains(pointee)) {
				    	continue;
					}
					dl.log(dl.intraProc && pointee > 0,"Mark %s -> %d\n", ac.to.getName(), pointee);
				}
				if (pts.get(ac.to).addAll(pts.get(ac.from))) {
					flag = true;
				}
			}
		}
	}
	TreeSet<Integer> getPointsToSet(Local local) {
		return pts.get(local);
	}
	
}
