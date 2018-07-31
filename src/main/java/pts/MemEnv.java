package pts;

import soot.Local;
import soot.SootField;
import soot.jimple.*;

import java.util.*;

/**
 * A snapshot of a memory environment.
 */
public class MemEnv {
    private final Map<Integer, Set<Integer>> mem = new HashMap<>();
    private final Map<Integer, Map<String, Set<Integer>>> mem_f = new HashMap<>();

    private final List<MemEnv> parents = new ArrayList<>();

    private boolean assignUpdated = false;

    public Set<Integer> getPTS(Integer obj, String field)
    {
        if (field == null)
        {
            if (!mem.containsKey(obj))
            {
                mem.put(obj, new TreeSet<>());
            }
            return mem.get(obj);
        } else {
            if (!mem_f.containsKey(obj))
            {
                mem_f.put(obj, new HashMap<>());
            }
            if (!mem_f.get(obj).containsKey(field)) {
                mem_f.get(obj).put(field, new TreeSet<>());
            }
            System.out.println("get field pts id " + mem_f.get(obj).get(field));
            return mem_f.get(obj).get(field);
        }
    }

    public void replace(MemEnv env)
    {
        mem.clear();
        mem.putAll(env.mem);
        mem_f.clear();
        mem_f.putAll(env.mem_f);
    }

    public void addParent(MemEnv env)
    {
        parents.add(env);
    }

    public void resetParent()
    {
        parents.clear();
    }

    public boolean addPTS(Integer base, String field, Set<Integer> pts)
    {
        boolean updated;
        if (field == null)
        {
            if (!mem.containsKey(base))
            {
                mem.put(base, new TreeSet<>());
            }
            updated = mem.get(base).addAll(pts);

        } else {
            if (!mem_f.containsKey(base))
            {
                mem_f.put(base, new HashMap<>());
            }
            if (!mem_f.get(base).containsKey(field)) {
                mem_f.get(base).put(field, new TreeSet<>());
            }
            updated = mem_f.get(base).get(field).addAll(pts);
        }
        assignUpdated |= updated;
        return updated;
    }

    public boolean update()
    {
        boolean updated = assignUpdated;
        assignUpdated = false;

        for (MemEnv parent : parents) {
            for (Map.Entry<Integer, Set<Integer>> entry : parent.mem.entrySet())
            {
                Integer key = entry.getKey();
                Set<Integer> value = entry.getValue();
                if (!mem.containsKey(entry.getKey()))
                {
                    mem.put(key, value);
                    updated = true;
                } else
                {
                    updated |= mem.get(key).addAll(value);
                }
            }
            for (Map.Entry<Integer,Map<String, Set<Integer>>> entry : parent.mem_f.entrySet())
            {
                Integer key = entry.getKey();
                Map<String, Set<Integer>> entry_value = entry.getValue();

                if (!mem_f.containsKey(entry.getKey()))
                {
                    mem_f.put(key, entry_value);
                    updated = true;
                } else
                {
                    for (Map.Entry<String, Set<Integer>> entry1 : entry.getValue().entrySet())
                    {
                        String field = entry1.getKey();
                        Set<Integer> entry1_value = entry1.getValue();
                        if (!mem_f.get(key).containsKey(entry1.getKey())) {
                            mem_f.get(key).put(field, entry1_value);
                            updated = true;
                        }
                        updated |= mem_f.get(key).get(field).addAll(entry1_value);
                    }
                }
            }
        }

        return updated;
    }

    @Override
    public String toString()
    {
        return mem.toString() + mem_f.toString();
    }

    public boolean mergeEnv(MemEnv memEnv) {
        boolean updated = false;
        for (Map.Entry<Integer, Set<Integer>> entry : memEnv.mem.entrySet())
        {
            updated |= addPTS(entry.getKey(), null /* TODO */, entry.getValue());
        }
        for (Map.Entry<Integer, Map<String, Set<Integer>>> entry : memEnv.mem_f.entrySet())
        {
            for (Map.Entry<String, Set<Integer>> entry1 : entry.getValue().entrySet())
            {
                updated |= addPTS(entry.getKey(), entry1.getKey() /* TODO */, entry1.getValue());
            }
        }
        return updated;
    }
}
