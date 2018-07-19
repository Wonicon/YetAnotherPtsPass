package pts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import soot.Local;

class AssignConstraint {
	Local from, to;
	AssignConstraint(Local from, Local to) {
		this.from = from;
		this.to = to;
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

public class Anderson {

	private List<AssignConstraint> assignConstraintList = new ArrayList<>();
	private List<NewConstraint> newConstraintList = new ArrayList<>();

	private Map<Local, TreeSet<Integer>> pts = new HashMap<>();

	void addAssignConstraint(Local from, Local to) {
		assignConstraintList.add(new AssignConstraint(from, to));
	}

	void addNewConstraint(int alloc, Local to) {
		newConstraintList.add(new NewConstraint(alloc, to));		
	}

	void run() {
		DebugLogger dl;
	    dl = new DebugLogger();

		for (NewConstraint nc : newConstraintList) {
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
			for (AssignConstraint ac : assignConstraintList) {
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
