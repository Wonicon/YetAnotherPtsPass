package pts;

import com.sun.istack.internal.NotNull;
import soot.Local;
import soot.SootField;
import soot.jimple.*;

import java.util.*;

/**
 * A snapshot of a memory environment.
 */
public class MemEnv {
    private final Map<Integer, Set<Integer>> mem = new HashMap<>();

    private final List<MemEnv> parents = new ArrayList<>();

    private boolean assignUpdated = false;

    public Set<Integer> getPTS(Integer obj, SootField field)
    {
        if (!mem.containsKey(obj)) {
            mem.put(obj, new TreeSet<>());
        }
        return mem.get(obj);
    }

    public void replace(@NotNull MemEnv env)
    {
        mem.clear();
        mem.putAll(env.mem);
    }

    public void addParent(MemEnv env)
    {
        parents.add(env);
    }

    public void resetParent()
    {
        parents.clear();
    }

    public boolean addPTS(Integer base, SootField field, Set<Integer> pts)
    {
        if (!mem.containsKey(base)) {
            mem.put(base, new TreeSet<>());
        }
        boolean updated = mem.get(base).addAll(pts);
        assignUpdated |= updated;
        return updated;
    }

    public boolean update()
    {
        boolean updated = assignUpdated;
        assignUpdated = false;

        for (MemEnv parent : parents) {
            for (Map.Entry<Integer, Set<Integer>> entry : parent.mem.entrySet()) {
                Integer key = entry.getKey();
                Set<Integer> value = entry.getValue();
                if (!mem.containsKey(entry.getKey())) {
                    mem.put(key, value);
                    updated = true;
                }
                else {
                    updated |= mem.get(key).addAll(value);
                }
            }
        }

        return updated;
    }

    @Override
    public String toString()
    {
        return mem.toString();
    }

    public boolean mergeEnv(MemEnv memEnv) {
        boolean updated = false;
        for (Map.Entry<Integer, Set<Integer>> entry : memEnv.mem.entrySet()) {
            updated |= addPTS(entry.getKey(), null /* TODO */, entry.getValue());
        }
        return updated;
    }
}
