package net.sf.cpsolver.studentsct.extension;

import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;

import net.sf.cpsolver.ifs.extension.Extension;
import net.sf.cpsolver.ifs.solver.Solver;
import net.sf.cpsolver.ifs.util.DataProperties;
import net.sf.cpsolver.studentsct.StudentSectioningModel;
import net.sf.cpsolver.studentsct.model.Assignment;
import net.sf.cpsolver.studentsct.model.Enrollment;
import net.sf.cpsolver.studentsct.model.FreeTimeRequest;
import net.sf.cpsolver.studentsct.model.Request;
import net.sf.cpsolver.studentsct.model.Student;

/**
 * This extension computes time overlaps. Only sections that allow overlaps
 * (see {@link Assignment#isAllowOverlap()}) can overlap. This class counts
 * how many overlapping slots there are so that this number can be minimized.
 * 
 * <br>
 * <br>
 * 
 * @version StudentSct 1.2 (Student Sectioning)<br>
 *          Copyright (C) 2007 - 2010 Tomas Muller<br>
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

public class TimeOverlapsCounter extends Extension<Request, Enrollment> {
    private static Logger sLog = Logger.getLogger(TimeOverlapsCounter.class);
    private int iTotalNrConflicts = 0;
    private Set<Conflict> iAllConflicts = new HashSet<Conflict>();
    /** Debug flag */
    public static boolean sDebug = false;
    private Request iOldVariable = null;

    /**
     * Constructor. Beside of other things, this constructor also uses
     * {@link StudentSectioningModel#setTimeOverlaps(TimeOverlapsCounter)} to
     * set the this instance to the model.
     * 
     * @param solver
     *            constraint solver
     * @param properties
     *            configuration
     */
    public TimeOverlapsCounter(Solver<Request, Enrollment> solver, DataProperties properties) {
        super(solver, properties);
        if (solver != null)
            ((StudentSectioningModel) solver.currentSolution().getModel()).setTimeOverlaps(this);
    }

    /**
     * Initialize extension
     */
    @Override
    public boolean init(Solver<Request, Enrollment> solver) {
        iTotalNrConflicts = countTotalNrConflicts();
        return true;
    }

    @Override
    public String toString() {
        return "TimeOverlaps";
    }

    /**
     * Return true if the given two assignments are overlapping.
     * 
     * @param a1
     *            an assignment
     * @param a2
     *            an assignment
     * @return true, if the given sections are in an overlapping conflict
     */
    public boolean inConflict(Assignment a1, Assignment a2) {
        if (a1.getTime() == null || a2.getTime() == null) return false;
        return a1.getTime().hasIntersection(a2.getTime());
    }
    
    /**
     * If the two sections are overlapping, return the number of slots of the overlap.
     * 
     * @param a1
     *            an assignment
     * @param a2
     *            an assignment
     * @return the number of overlapping slots against the number of slots of the smallest section
     */
    public int share(Assignment a1, Assignment a2) {
        if (!inConflict(a1, a2)) return 0;
        return a1.getTime().nrSharedDays(a2.getTime()) * a1.getTime().nrSharedHours(a2.getTime());
    }


    /**
     * Return number of time overlapping conflicts that are between two enrollments. It
     * is the total share between pairs of assignments of these enrollments that are in a
     * time overlap.
     * 
     * @param e1
     *            an enrollment
     * @param e2
     *            an enrollment
     * @return number of time overlapping conflict between given enrollments
     */
    public int nrConflicts(Enrollment e1, Enrollment e2) {
        if (!e1.getStudent().equals(e2.getStudent())) return 0;
        if (e1.getRequest() instanceof FreeTimeRequest && e2.getRequest() instanceof FreeTimeRequest) return 0;
        int cnt = 0;
        for (Assignment s1 : e1.getAssignments()) {
            for (Assignment s2 : e2.getAssignments()) {
                if (inConflict(s1, s2))
                    cnt += share(s1, s2);
            }
        }
        return cnt;
    }

    /**
     * Return a set of time overlapping conflicts ({@link Conflict} objects) between
     * given (course) enrollments.
     * 
     * @param e1
     *            an enrollment
     * @param e2
     *            an enrollment
     * @return list of time overlapping conflicts that are between assignment of the
     *         given enrollments
     */
    public Set<Conflict> conflicts(Enrollment e1, Enrollment e2) {
        Set<Conflict> ret = new HashSet<Conflict>();
        if (!e1.getStudent().equals(e2.getStudent())) return ret;
        if (e1.getRequest() instanceof FreeTimeRequest && e2.getRequest() instanceof FreeTimeRequest) return ret;
        for (Assignment s1 : e1.getAssignments()) {
            for (Assignment s2 : e2.getAssignments()) {
                if (inConflict(s1, s2))
                    ret.add(new Conflict(e1.getStudent(), s1, s2));
            }
        }
        return ret;
    }

    /**
     * Total sum of all conflict of the given enrollment and other enrollments
     * that are assigned to the same student.
     */
    public int nrAllConflicts(Enrollment enrollment) {
        if (enrollment.getRequest() instanceof FreeTimeRequest) return 0;
        int cnt = 0;
        for (Request request : enrollment.getStudent().getRequests()) {
            if (request.equals(enrollment.getRequest())) continue;
            if (request instanceof FreeTimeRequest) {
                FreeTimeRequest ft = (FreeTimeRequest)request;
                cnt += nrConflicts(enrollment, ft.createEnrollment());
            } else if (request.getAssignment() != null && !request.equals(iOldVariable)) {
                cnt += nrConflicts(enrollment, request.getAssignment());
            }
        }
        return cnt;
    }

    /**
     * The set of all conflicts ({@link Conflict} objects) of the given
     * enrollment and other enrollments that are assigned to the same student.
     */
    public Set<Conflict> allConflicts(Enrollment enrollment) {
        Set<Conflict> ret = new HashSet<Conflict>();
        if (enrollment.getRequest() instanceof FreeTimeRequest) return ret;
        for (Request request : enrollment.getStudent().getRequests()) {
            if (request.equals(enrollment.getRequest())) continue;
            if (request instanceof FreeTimeRequest) {
                FreeTimeRequest ft = (FreeTimeRequest)request;
                ret.addAll(conflicts(enrollment, ft.createEnrollment()));
                continue;
            } else if (request.getAssignment() != null && !request.equals(iOldVariable)) {
                ret.addAll(conflicts(enrollment, request.getAssignment()));
            }
        }
        return ret;
    }

    /**
     * Called when a value is assigned to a variable. Internal number of
     * time overlapping conflicts is updated, see
     * {@link TimeOverlapsCounter#getTotalNrConflicts()}.
     */
    public void assigned(long iteration, Enrollment value) {
        int inc = nrAllConflicts(value);
        iTotalNrConflicts += inc;
        if (sDebug) {
            sLog.debug("A:" + value.variable() + " := " + value);
            if (inc != 0) {
                sLog.debug("-- TOC+" + inc + " A: " + value.variable() + " := " + value);
                for (Conflict c: allConflicts(value)) {
                    sLog.debug("  -- " + c);
                    iAllConflicts.add(c);
                    inc -= c.getShare();
                }
                if (inc != 0) {
                    sLog.error("Different numbers " + nrAllConflicts(value) + " != " + allConflicts(value));
                }
            }

            Set<Conflict> allConfs = computeAllConflicts();
            int allConfShare = 0;
            for (Conflict c: allConfs)
                allConfShare += c.getShare();
            
            if (iTotalNrConflicts != allConfShare) {
                sLog.error("Different number of conflicts " + iTotalNrConflicts + "!=" + allConfShare);
                for (Conflict c: allConfs) {
                    if (!iAllConflicts.contains(c))
                        sLog.debug("  +add+ " + c);
                }
                for (Conflict c: iAllConflicts) {
                    if (!allConfs.contains(c))
                        sLog.debug("  -rem- " + c);
                }
                for (Conflict c: allConfs) {
                    for (Conflict d: iAllConflicts) {
                        if (c.equals(d) && c.getShare() != d.getShare()) {
                            sLog.debug("  -dif- " + c + " (other: " + d.getShare() + ")");
                        }
                    }
                }                
                iTotalNrConflicts = allConfShare;
                iAllConflicts = allConfs;
            }
        }
    }

    /**
     * Called when a value is unassigned from a variable. Internal number of
     * time overlapping conflicts is updated, see
     * {@link TimeOverlapsCounter#getTotalNrConflicts()}.
     */
    public void unassigned(long iteration, Enrollment value) {
        int dec = nrAllConflicts(value);
        iTotalNrConflicts -= dec;
        if (sDebug) {
            sLog.debug("U:" + value.variable() + " := " + value);
            if (dec != 0) {
                sLog.debug("-- TOC-" + dec + " U: " + value.variable() + " := " + value);
                for (Conflict c: allConflicts(value)) {
                    sLog.debug("  -- " + c);
                    iAllConflicts.remove(c);
                    dec -= c.getShare();
                }
                if (dec != 0) {
                    sLog.error("Different numbers " + nrAllConflicts(value) + " != " + allConflicts(value));
                }
            }
            
            Set<Conflict> allConfs = computeAllConflicts();
            int allConfShare = 0;
            for (Conflict c: allConfs)
                allConfShare += c.getShare();
            
            if (iTotalNrConflicts != allConfShare) {
                sLog.error("Different number of conflicts " + iTotalNrConflicts + "!=" + allConfShare);
                for (Conflict c: allConfs) {
                    if (!iAllConflicts.contains(c))
                        sLog.debug("  +add+ " + c);
                }
                for (Conflict c: iAllConflicts) {
                    if (!allConfs.contains(c))
                        sLog.debug("  -rem- " + c);
                }
                for (Conflict c: allConfs) {
                    for (Conflict d: iAllConflicts) {
                        if (c.equals(d) && c.getShare() != d.getShare()) {
                            sLog.debug("  -dif- " + c + " (other: " + d.getShare() + ")");
                        }
                    }
                }                
                iTotalNrConflicts = allConfShare;
                iAllConflicts = allConfs;
            }
        }
    }

    /** Actual number of all time overlapping conflicts */
    public int getTotalNrConflicts() {
        return iTotalNrConflicts;
    }

    /**
     * Compute the actual number of all time overlapping conflicts. Should be equal to
     * {@link TimeOverlapsCounter#getTotalNrConflicts()}.
     */
    public int countTotalNrConflicts() {
        int total = 0;
        for (Request r1 : getModel().variables()) {
            if (r1.getAssignment() == null || r1 instanceof FreeTimeRequest || r1.equals(iOldVariable))
                continue;
            for (Request r2 : r1.getStudent().getRequests()) {
                if (r2 instanceof FreeTimeRequest) {
                    FreeTimeRequest ft = (FreeTimeRequest)r2;
                    total += nrConflicts(r1.getAssignment(), ft.createEnrollment());
                } else if (r2.getAssignment() != null && r1.getId() < r2.getId() && !r2.equals(iOldVariable)) {
                    total += nrConflicts(r1.getAssignment(), r2.getAssignment());
                }
            }
        }
        return total;
    }

    /**
     * Compute a set of all time overlapping conflicts ({@link Conflict} objects).
     */
    public Set<Conflict> computeAllConflicts() {
        Set<Conflict> ret = new HashSet<Conflict>();
        for (Request r1 : getModel().variables()) {
            if (r1.getAssignment() == null || r1 instanceof FreeTimeRequest || r1.equals(iOldVariable))
                continue;
            for (Request r2 : r1.getStudent().getRequests()) {
                if (r2 instanceof FreeTimeRequest) {
                    FreeTimeRequest ft = (FreeTimeRequest)r2;
                    ret.addAll(conflicts(r1.getAssignment(), ft.createEnrollment()));
                } else if (r2.getAssignment() != null && r1.getId() < r2.getId() && !r2.equals(iOldVariable)) {
                    ret.addAll(conflicts(r1.getAssignment(), r2.getAssignment()));
                }                    
            }
        }
        return ret;
    }

    /**
     * Called before a value is assigned to a variable.
     */
    @Override
    public void beforeAssigned(long iteration, Enrollment value) {
        if (value != null) {
            if (value.variable().getAssignment() != null)
                unassigned(iteration, value.variable().getAssignment());
            iOldVariable = value.variable();
        }
    }

    /**
     * Called after a value is assigned to a variable.
     */
    @Override
    public void afterAssigned(long iteration, Enrollment value) {
        iOldVariable = null;
        if (value != null) {
            assigned(iteration, value);
        }
    }

    /**
     * Called after a value is unassigned from a variable.
     */
    @Override
    public void afterUnassigned(long iteration, Enrollment value) {
        if (value != null) {
            unassigned(iteration, value);
        }
    }

    /** A representation of a time overlapping conflict */
    public class Conflict {
        private int iShare;
        private Student iStudent;
        private Assignment iA1, iA2;
        private int iHashCode;

        /**
         * Constructor
         * 
         * @param student
         *            related student
         * @param a1
         *            first conflicting section
         * @param a2
         *            second conflicting section
         */
        public Conflict(Student student, Assignment a1, Assignment a2) {
            iStudent = student;
            if (a1.compareById(a2) < 0 ) {
                iA1 = a1;
                iA2 = a2;
            } else {
                iA1 = a2;
                iA2 = a1;
            }
            iHashCode = (iStudent.getId() + ":" + iA1.getId() + ":" + iA2.getId()).hashCode();
            iShare = share(getS1(), getS2());
        }

        /** Related student */
        public Student getStudent() {
            return iStudent;
        }

        /** First section */
        public Assignment getS1() {
            return iA1;
        }

        /** Second section */
        public Assignment getS2() {
            return iA2;
        }

        @Override
        public int hashCode() {
            return iHashCode;
        }

        /** The number of overlapping slots against the number of slots of the smallest section */
        public int getShare() {
            return iShare;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || !(o instanceof Conflict)) return false;
            Conflict c = (Conflict) o;
            return getStudent().equals(c.getStudent()) && getS1().equals(c.getS1()) && getS2().equals(c.getS2());
        }

        @Override
        public String toString() {
            return getStudent() + ": (s:" + getShare() + ") " + getS1() + " -- " + getS2();
        }
    }
}
