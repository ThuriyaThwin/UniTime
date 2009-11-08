package net.sf.cpsolver.ifs.dbt;


import java.util.*;

import net.sf.cpsolver.ifs.extension.*;
import net.sf.cpsolver.ifs.model.*;
import net.sf.cpsolver.ifs.solver.*;
import net.sf.cpsolver.ifs.util.*;

/**
 * Maintenance of arc consistency in dynamic backtracking.
 * <br><br>
 * The difference between {@link MacPropagation} and this DBT propagation is that all not-assigned values 
 * of an assigned variable are marked as nogood. Also, when a dead end is reached, unassignment or failure
 * takes place.
 * <br><br>
 * This IFS solver extension is to be used only in case of dynamic backtracking and it has no parameters.
 *
 *
 * @version
 * IFS 1.1 (Iterative Forward Search)<br>
 * Copyright (C) 2006 Tomas Muller<br>
 * <a href="mailto:muller@unitime.org">muller@unitime.org</a><br>
 * Lazenska 391, 76314 Zlin, Czech Republic<br>
 * <br>
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * <br><br>
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * <br><br>
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *  
 */
public class DbtPropagation extends MacPropagation implements SolverListener {
    private static org.apache.log4j.Logger sLogger = org.apache.log4j.Logger.getLogger(DbtPropagation.class);
    
    /** 
     * Constructor. No parameter is taken from properties.
     */
    public DbtPropagation(Solver solver, DataProperties properties) {
        super(solver, properties);
        solver.addSolverListener(this);
    }
    
    /**
     * Propagation takes place every time a value is assigned to a variable.
     * <br><br>
     * <li>Prints a warning if the value is nogood (should not never happen),
     * <li>sets all other values of the variable to nogood (explanation is the assigned value itself),
     * <li>runs propagation.
     *
     * @see MacPropagation#propagate(Variable)
     */
    public void afterAssigned(long iteration, Value value) {
        iIteration = iteration;
        if (!isGood(value)) {
            sLogger.warn(value.variable().getName()+" = "+value.getName()+" -- not good value assigned (noGood:"+noGood(value)+")");
            setGood(value);
        }
        
        HashSet noGood = new HashSet(1);
        
        noGood.add(value);
        for (Enumeration i = value.variable().values().elements(); i.hasMoreElements();) {
            Value anotherValue = (Value) i.nextElement();
            if (anotherValue.equals(value) || !isGood(anotherValue)) continue;
            setNoGood(anotherValue, noGood);
        }
        propagate(value.variable());
    }
    
    /**
     * Undo propagation when a value is unassigned.
     * <br><br>
     * <li>Prints an error if the value is nogood (should not never happen),
     * <li>runs propagation undo.
     *
     * @see MacPropagation#undoPropagate(Variable)
     */
    public void afterUnassigned(long iteration, Value value) {
        iIteration = iteration;
        if (!isGood(value)) {
            sLogger.error(value.variable().getName()+" = "+value.getName()+" -- not good value unassigned (noGood:"+noGood(value)+")");
        }
        undoPropagate(value.variable());
    }
    
    /**
     * If no variable is selected (all variables are assinged), unassign the last assigned variable.
     * <br><br>
     * Do not allow to select an assigned variable.
     * <br><br>
     * If no variable is selected (because all variables are assigned, see {@link DbtVariableSelection}):
     * <ul>
     * <li> find the last assigned variable and 
     * <li> unassign it (explanation is a union of assignments of all other variables).
     * </ul>
     *
     * @see DbtVariableSelection#selectVariable(Solution)
     */
    public boolean variableSelected(long iteration, Variable variable) {
        if (variable == null) {
            sLogger.debug("No variable selected -> backtrack.");
            Variable lastVariable = null;
            
            for (Enumeration i = getModel().assignedVariables().elements(); i.hasMoreElements();) {
                Variable var = (Variable) i.nextElement();
                
                if (lastVariable == null || lastVariable.getAssignment().lastAssignmentIteration()<var.getAssignment().lastAssignmentIteration()) {
                    lastVariable = var;
                }
            }
            if (lastVariable == null) {
                sLogger.error("No assignment -> fail");
                getSolver().stopSolver();
                return false;
            }
            sLogger.debug("Unassign:" + lastVariable.getName());
            HashSet noGoods = new HashSet();
            
            for (Enumeration i = getModel().assignedVariables().elements(); i.hasMoreElements();) {
                Variable var = (Variable) i.nextElement();
                
                if (!var.equals(lastVariable)) {
                    noGoods.add(var.getAssignment());
                }
            }
            Value value = lastVariable.getAssignment();
            
            lastVariable.unassign(iteration);
            setNoGood(value, noGoods);
            return false;
        }
        if (variable.getAssignment() != null) {
            sLogger.error("Assigned value selected -- not supported by DBT.");
            return false;
        }
        return true;
    }
    
    /**
     * If no value is selected (because of a dead end), make some unassignments.
     * <br><br>
     * If no value is selected 
     * (e.g., because the selected variable has all values marked as nogood, see {@link DbtValueSelection}),
     * <ul>
     * <li> compute a union of explanations of all values, 
     * <ul><li> if it is empty fail (inconsistency is found),</ul>
     * <li> otherwise pick the last assigned variable from the computed union of explanation and unassign it
     * <ul>(explanation for that is the computed union of explanations without the last assignment).</ul>
     * </ul>
     *
     * @see DbtVariableSelection#selectVariable(Solution)
     */
    public boolean valueSelected(long iteration, Variable variable, Value value) {
        if (variable != null && value == null) {
            HashSet noGoods = new HashSet();
            
            for (Enumeration i = variable.values().elements(); i.hasMoreElements();) {
                Value val = (Value) i.nextElement();
                if (noGood(val) != null) {
                    noGoods.addAll(noGood(val));
                }
            }
            if (noGoods.isEmpty()) {
                sLogger.debug("Fail");
                getSolver().stopSolver();
                return false;
            }
            Variable lastVariable = null;
            
            for (Iterator i = noGoods.iterator(); i.hasNext();) {
                Value val = (Value) i.next();
                Variable var = val.variable();
                
                if (lastVariable == null || lastVariable.getAssignment().lastAssignmentIteration()<var.getAssignment().lastAssignmentIteration()) {
                    lastVariable = var;
                }
            }
            Value assignment = lastVariable.getAssignment();
            
            noGoods.remove(assignment);
            lastVariable.unassign(iteration);
            setNoGood(assignment, noGoods);
        }
        return true;
    }
    
    public boolean neighbourSelected(long iteration, Neighbour neighbour) {
    	return true;
    }
}