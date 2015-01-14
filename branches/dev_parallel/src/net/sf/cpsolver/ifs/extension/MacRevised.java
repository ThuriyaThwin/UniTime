package net.sf.cpsolver.ifs.extension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sf.cpsolver.ifs.assignment.Assignment;
import net.sf.cpsolver.ifs.assignment.context.AssignmentContext;
import net.sf.cpsolver.ifs.assignment.context.ExtensionWithContext;
import net.sf.cpsolver.ifs.model.Constraint;
import net.sf.cpsolver.ifs.model.Value;
import net.sf.cpsolver.ifs.model.Variable;
import net.sf.cpsolver.ifs.solver.Solver;
import net.sf.cpsolver.ifs.util.DataProperties;
import net.sf.cpsolver.ifs.util.Progress;

/**
 * Another implementation of MAC propagation.
 * 
 * @see MacPropagation
 * 
 * @version IFS 1.2 (Iterative Forward Search)<br>
 *          Copyright (C) 2006 - 2010 Tomas Muller<br>
 *          <a href="mailto:muller@unitime.org">muller@unitime.org</a><br>
 *          <a href="http://muller.unitime.org">http://muller.unitime.org</a><br>
 * <br>
 *          This library is free software; you can redistribute it and/or modify
 *          it under the terms of the GNU Lesser General Public License as
 *          published by the Free Software Foundation; either version 3 of the
 *          License, or (at your option) any later version. <br>
 * <br>
 *          This library is distributed in the hope that it will be useful, but
 *          WITHOUT ANY WARRANTY; without even the implied warranty of
 *          MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *          Lesser General Public License for more details. <br>
 * <br>
 *          You should have received a copy of the GNU Lesser General Public
 *          License along with this library; if not see
 *          <a href='http://www.gnu.org/licenses/'>http://www.gnu.org/licenses/</a>.
 */

public class MacRevised<V extends Variable<V, T>, T extends Value<V, T>> extends ExtensionWithContext<V, T, MacRevised<V, T>.NoGood> {
    private static org.apache.log4j.Logger sLogger = org.apache.log4j.Logger.getLogger(MacRevised.class);
    private boolean iDbt = false;
    private Progress iProgress;

    /** List of constraints on which arc-consistency is to be maintained */
    protected List<Constraint<V, T>> iConstraints = null;
    /** Current iteration */
    protected long iIteration = 0;

    /** Constructor */
    public MacRevised(Solver<V, T> solver, DataProperties properties) {
        super(solver, properties);
        iDbt = properties.getPropertyBoolean("MacRevised.Dbt", false);
    }

    /** Adds a constraint on which arc-consistency is to be maintained */
    public void addConstraint(Constraint<V, T> constraint) {
        if (iConstraints == null)
            iConstraints = new ArrayList<Constraint<V, T>>();
        iConstraints.add(constraint);
    }

    /**
     * Returns true, if arc-consistency is to be maintained on the given
     * constraint
     */
    public boolean contains(Constraint<V, T> constraint) {
        if (iConstraints == null)
            return true;
        return iConstraints.contains(constraint);
    }

    /**
     * Before a value is unassigned: until the value is inconsistent with the
     * current solution, an assignment from its explanation is picked and
     * unassigned.
     */
    @Override
    public void beforeAssigned(Assignment<V, T> assignment, long iteration, T value) {
        if (value == null)
            return;
        sLogger.debug("Before assign " + value.variable().getName() + " = " + value.getName());
        iIteration = iteration;
        while (!isGood(assignment, value) && !noGood(assignment, value).isEmpty()) {
            if (iDbt)
                sLogger.warn("Going to assign a no-good value " + value + " (noGood:" + noGood(assignment, value) + ").");
            T noGoodValue = noGood(assignment, value).iterator().next();
            assignment.unassign(iteration, noGoodValue.variable());
        }
        if (!isGood(assignment, value)) {
            sLogger.warn("Going to assign a bad value " + value + " with empty no-good.");
        }
    }

    /**
     * After a value is assigned: explanations of other values of the value's
     * variable are reset (to contain only the assigned value), propagation over
     * the assigned variable takes place.
     */
    @Override
    public void afterAssigned(Assignment<V, T> assignment, long iteration, T value) {
        sLogger.debug("After assign " + value.variable().getName() + " = " + value.getName());
        iIteration = iteration;
        if (!isGood(assignment, value)) {
            sLogger.warn(value.variable().getName() + " = " + value.getName() + " -- not good value assigned (noGood:" + noGood(assignment, value) + ")");
            setGood(assignment, value);
        }

        Set<T> noGood = new HashSet<T>(1);
        noGood.add(value);
        List<T> queue = new ArrayList<T>();
        for (Iterator<T> i = value.variable().values().iterator(); i.hasNext();) {
            T anotherValue = i.next();
            if (anotherValue.equals(value) || !isGood(assignment, anotherValue))
                continue;
            setNoGood(assignment, anotherValue, noGood);
            queue.add(anotherValue);
        }
        propagate(assignment, queue);
    }

    /**
     * After a value is unassigned: explanations of all values of unassigned
     * variable are recomputed ({@link Value#conflicts(Assignment)}), propagation undo
     * over the unassigned variable takes place.
     */
    @Override
    public void afterUnassigned(Assignment<V, T> assignment, long iteration, T value) {
        sLogger.debug("After unassign " + value.variable().getName() + " = " + value.getName());
        iIteration = iteration;
        if (!isGood(assignment, value))
            sLogger.error(value.variable().getName() + " = " + value.getName()
                    + " -- not good value unassigned (noGood:" + noGood(assignment, value) + ")");

        List<T> back = new ArrayList<T>(supportValues(assignment, value.variable()));
        for (T aValue : back) {
            T current = assignment.getValue(aValue.variable());
            if (current != null) {
                Set<T> noGood = new HashSet<T>(1);
                noGood.add(current);
                setNoGood(assignment, aValue, noGood);
            } else
                setGood(assignment, aValue);
        }

        List<T> queue = new ArrayList<T>();
        for (T aValue : back) {
            if (!isGood(assignment, aValue) || revise(assignment, aValue))
                queue.add(aValue);
        }

        propagate(assignment, queue);
    }

    public void propagate(Assignment<V, T> assignment, List<T> queue) {
        int idx = 0;
        while (queue.size() > idx) {
            T value = queue.get(idx++);
            sLogger.debug("  -- propagate " + value.variable().getName() + " = " + value.getName() + " (noGood:"
                    + noGood(assignment, value) + ")");
            if (goodValues(assignment, value.variable()).isEmpty()) {
                sLogger.info("Empty domain detected for variable " + value.variable().getName());
                continue;
            }
            for (Constraint<V, T> constraint : value.variable().hardConstraints()) {
                if (!contains(constraint))
                    continue;
                propagate(assignment, constraint, value, queue);
            }
        }
    }

    public void propagate(Assignment<V, T> assignment, Constraint<V, T> constraint, T noGoodValue, List<T> queue) {
        for (V aVariable : constraint.variables()) {
            if (aVariable.equals(noGoodValue.variable()))
                continue;
            for (Iterator<T> j = aVariable.values().iterator(); j.hasNext();) {
                T aValue = j.next();
                if (isGood(assignment, aValue) && constraint.isConsistent(noGoodValue, aValue)
                        && !hasSupport(assignment, constraint, aValue, noGoodValue.variable())) {
                    setNoGood(assignment, aValue, explanation(assignment, constraint, aValue, noGoodValue.variable()));
                    queue.add(aValue);
                }
            }
        }
    }

    public boolean revise(Assignment<V, T> assignment, T value) {
        sLogger.debug("  -- revise " + value.variable().getName() + " = " + value.getName());
        for (Constraint<V, T> constraint : value.variable().hardConstraints()) {
            if (!contains(constraint))
                continue;
            if (revise(assignment, constraint, value))
                return true;
        }
        return false;
    }

    public boolean revise(Assignment<V, T> assignment, Constraint<V, T> constraint, T value) {
        for (V aVariable : constraint.variables()) {
            if (aVariable.equals(value.variable()))
                continue;
            if (!hasSupport(assignment, constraint, value, aVariable)) {
                setNoGood(assignment, value, explanation(assignment, constraint, value, aVariable));
                return true;
            }
        }
        return false;
    }

    public Set<T> explanation(Assignment<V, T> assignment, Constraint<V, T> constraint, T value, V variable) {
        Set<T> expl = new HashSet<T>();
        for (T aValue : variable.values()) {
            if (constraint.isConsistent(aValue, value)) {
                expl.addAll(noGood(assignment, aValue));
            }
        }
        return expl;
    }

    public Set<T> supports(Assignment<V, T> assignment, Constraint<V, T> constraint, T value, V variable) {
        Set<T> sup = new HashSet<T>();
        for (T aValue : variable.values()) {
            if (!isGood(assignment, aValue))
                continue;
            if (!constraint.isConsistent(aValue, value))
                continue;
            sup.add(aValue);
        }
        return sup;
    }

    public boolean hasSupport(Assignment<V, T> assignment, Constraint<V, T> constraint, T value, V variable) {
        for (T aValue : variable.values()) {
            if (isGood(assignment, aValue) && constraint.isConsistent(aValue, value)) {
                // sLogger.debug("    -- "+variable.getName()+" = "+aValue.getName()+" supports "
                // +
                // value.variable().getName()+" = "+value.getName()+" (constraint:"+constraint.getName()+")");
                return true;
            }
        }
        // sLogger.debug("    -- value "+value.variable().getName()+" = " +
        // value.getName()+" has no support from values of variable "+variable.getName()+" (constraint:"+constraint.getName()+")");
        /*
         * for (Enumeration e=variable.values().elements();e.hasMoreElements();)
         * { T aValue = (T)e.nextElement(); if
         * (constraint.isConsistent(aValue,value)) {
         * //sLogger.debug("      -- support "
         * +aValue.getName()+" is not good: "+expl2str(noGood(aValue))); } }
         */
        return false;
    }

    /**
     * Initialization. Enforce arc-consistency over the current (initial)
     * solution. AC3 algorithm is used.
     */
    @Override
    public boolean init(Solver<V, T> solver) {
        return true;
    }

    /** support values of a variable */
    @SuppressWarnings("unchecked")
    private Set<T> supportValues(Assignment<V, T> assignment, V variable) {
        Set<T>[] ret = getContext(assignment).getNoGood(variable);
        if (ret == null) {
            ret = new Set[] { new HashSet<T>(1000), new HashSet<T>() };
            getContext(assignment).setNoGood(variable, ret);
        }
        return ret[0];
    }

    /** good values of a variable (values not removed from variables domain) */
    @SuppressWarnings("unchecked")
    public Set<T> goodValues(Assignment<V, T> assignment, V variable) {
        Set<T>[] ret = getContext(assignment).getNoGood(variable);
        if (ret == null) {
            ret = new Set[] { new HashSet<T>(1000), new HashSet<T>() };
            getContext(assignment).setNoGood(variable, ret);
        }
        return ret[1];
    }

    /** notification that a nogood value becomes good or vice versa */
    private void goodnessChanged(Assignment<V, T> assignment, T value) {
        if (isGood(assignment, value)) {
            goodValues(assignment, value.variable()).add(value);
        } else {
            goodValues(assignment, value.variable()).remove(value);
        }
    }

    /** removes support of a variable */
    private void removeSupport(Assignment<V, T> assignment, V variable, T value) {
        supportValues(assignment, variable).remove(value);
    }

    /** adds support of a variable */
    private void addSupport(Assignment<V, T> assignment, V variable, T value) {
        supportValues(assignment, variable).add(value);
    }

    /** variables explanation */
    public Set<T> noGood(Assignment<V, T> assignment, T value) {
        return getContext(assignment).getNoGood(value);
    }

    /** is variable good */
    public boolean isGood(Assignment<V, T> assignment, T value) {
        return (getContext(assignment).getNoGood(value) == null);
    }

    /** sets value to be good */
    protected void setGood(Assignment<V, T> assignment, T value) {
        sLogger.debug("    -- set good " + value.variable().getName() + " = " + value.getName());
        Set<T> noGood = noGood(assignment, value);
        if (noGood != null)
            for (T v : noGood)
                removeSupport(assignment, v.variable(), value);
        getContext(assignment).setNoGood(value, null);
        goodnessChanged(assignment, value);
    }

    /** sets values explanation (initialization) */
    private void initNoGood(Assignment<V, T> assignment, T value, Set<T> reason) {
        getContext(assignment).setNoGood(value, reason);
    }

    private String expl2str(Set<T> expl) {
        StringBuffer sb = new StringBuffer("[");
        for (Iterator<T> i = expl.iterator(); i.hasNext();) {
            T value = i.next();
            sb.append(value.variable().getName() + "=" + value.getName());
            if (i.hasNext())
                sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }

    private void checkExpl(Assignment<V, T> assignment, Set<T> expl) {
        sLogger.debug("    -- checking explanation: " + expl2str(expl));
        for (Iterator<T> i = expl.iterator(); i.hasNext();) {
            T value = i.next();
            T current = assignment.getValue(value.variable());
            if (!value.equals(current)) {
                if (current == null)
                    sLogger.warn("      -- variable " + value.variable().getName() + " unassigned");
                else
                    sLogger.warn("      -- variable " + value.variable().getName() + " assigned to a different value " + current.getName());
            }
        }
    }

    private void printAssignments(Assignment<V, T> assignment) {
        sLogger.debug("    -- printing assignments: ");
        for (Iterator<V> i = getModel().variables().iterator(); i.hasNext();) {
            V variable = i.next();
            T value = assignment.getValue(variable);
            if (value != null)
                sLogger.debug("      -- " + variable.getName() + " = " + value.getName());
        }
    }

    /** sets value's explanation */
    public void setNoGood(Assignment<V, T> assignment, T value, Set<T> reason) {
        sLogger.debug("    -- set nogood " + value.variable().getName() + " = " + value.getName() + "(expl:" + expl2str(reason) + ")");
        if (value.equals(assignment.getValue(value.variable()))) {
            try {
                throw new Exception("An assigned value " + value.variable().getName() + " = " + value.getName() + " become no good (noGood:" + reason + ")!!");
            } catch (Exception e) {
                sLogger.warn(e.getMessage(), e);
            }
            checkExpl(assignment, reason);
            printAssignments(assignment);
        }
        Set<T> noGood = noGood(assignment, value);
        if (noGood != null)
            for (T v : noGood)
                removeSupport(assignment, v.variable(), value);
        getContext(assignment).setNoGood(value, reason);
        for (T aValue : reason) {
            addSupport(assignment, aValue.variable(), value);
        }
        goodnessChanged(assignment, value);
    }
    
    @Override
    public NoGood createAssignmentContext(Assignment<V, T> assignment) {
        return new NoGood(assignment);
    }

    /**
     * Assignment context
     */
    public class NoGood implements AssignmentContext {
        private Map<V, Set<T>[]> iNoGood = new HashMap<V, Set<T>[]>();
        private Map<V, Map<T, Set<T>>> iNoGoodVal = new HashMap<V, Map<T, Set<T>>>();
        
        public NoGood(Assignment<V, T> assignment) {
            iProgress = Progress.getInstance(getModel());
            iProgress.save();
            iProgress.setPhase("Initializing propagation:", getModel().variables().size());
            for (Iterator<V> i = getModel().variables().iterator(); i.hasNext();) {
                V aVariable = i.next();
                supportValues(assignment, aVariable).clear();
                goodValues(assignment, aVariable).clear();
            }
            List<T> queue = new ArrayList<T>();
            for (Iterator<V> i = getModel().variables().iterator(); i.hasNext();) {
                V aVariable = i.next();
                for (Iterator<T> j = aVariable.values().iterator(); j.hasNext();) {
                    T aValue = j.next();
                    initNoGood(assignment, aValue, null);
                    goodValues(assignment, aVariable).add(aValue);
                    T current = assignment.getValue(aVariable);
                    if (revise(assignment, aValue)) {
                        queue.add(aValue);
                    } else if (current != null && !aValue.equals(current)) {
                        Set<T> noGood = new HashSet<T>();
                        noGood.add(current);
                        MacRevised.this.setNoGood(assignment, aValue, noGood);
                        queue.add(aValue);
                    }
                }
                iProgress.incProgress();
            }
            propagate(assignment, queue);
            iProgress.restore();
        }
        
        public Set<T>[] getNoGood(V variable) {
            return iNoGood.get(variable);
        }
        
        public void setNoGood(V variable, Set<T>[] noGood) {
            if (noGood == null)
                iNoGood.remove(variable);
            else
                iNoGood.put(variable, noGood);
        }
        
        public Set<T> getNoGood(T value) {
            Map<T, Set<T>> ng = iNoGoodVal.get(value.variable());
            if (ng == null) return null;
            return ng.get(value);
        }
        
        public void setNoGood(T value, Set<T> noGood) {
            Map<T, Set<T>> ng = iNoGoodVal.get(value.variable());
            if (ng == null) {
                ng = new HashMap<T, Set<T>>();
                iNoGoodVal.put(value.variable(), ng);
            }
            if (noGood == null)
                ng.remove(value);
            else
                ng.put(value, noGood);
        }
    }
}