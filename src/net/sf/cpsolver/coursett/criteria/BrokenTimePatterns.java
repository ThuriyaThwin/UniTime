package net.sf.cpsolver.coursett.criteria;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import net.sf.cpsolver.coursett.Constants;
import net.sf.cpsolver.coursett.constraint.RoomConstraint;
import net.sf.cpsolver.coursett.model.Lecture;
import net.sf.cpsolver.coursett.model.Placement;
import net.sf.cpsolver.coursett.model.RoomLocation;
import net.sf.cpsolver.coursett.model.TimeLocation;
import net.sf.cpsolver.coursett.model.TimetableModel;
import net.sf.cpsolver.ifs.util.DataProperties;

/**
 * Broken time patterns. This criterion counts cases when an unused space is in a room
 * which follows one of the standard MWF or TTh pattern. E.g., there is a penalty of
 * Monday is available during a time when Wednesday and/or Friday is occupied. The aim
 * is to use this space if possible in order to leave the available space in a way that 
 * can be used by MWF or TTh classes.
 * <br>
 * 
 * @version CourseTT 1.2 (University Course Timetabling)<br>
 *          Copyright (C) 2006 - 2011 Tomas Muller<br>
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
public class BrokenTimePatterns extends TimetablingCriterion {
    
    public BrokenTimePatterns() {
        iValueUpdateType = ValueUpdateType.NoUpdate;
    }
    
    @Override
    public double getWeightDefault(DataProperties config) {
        return Constants.sPreferenceLevelDiscouraged * config.getPropertyDouble("Comparator.UselessSlotWeight", 0.1);
    }
    
    @Override
    public String getPlacementSelectionWeightName() {
        return "Placement.UselessSlotsWeight";
    }
    
    protected int penalty(Placement value) {
        if (value.isMultiRoom()) {
            int ret = 0;
            for (RoomLocation r : value.getRoomLocations()) {
                if (r.getRoomConstraint() == null)
                    continue;
                ret += penalty(r.getRoomConstraint(), value.getTimeLocation());
            }
            return ret;
        } else {
            return (value.getRoomLocation().getRoomConstraint() == null ? 0 : penalty(value.getRoomLocation().getRoomConstraint(), value.getTimeLocation()));
        }
    }
    
    protected int penalty(RoomConstraint rc) {
        return countUselessSlotsBrokenTimePatterns(rc);
    }
    
   protected int penalty(RoomConstraint rc, TimeLocation value) {
        return countUselessSlotsBrokenTimePatterns(rc, value);
    }

   @Override
    public double getValue(Placement value, Set<Placement> conflicts) {
        double ret = penalty(value);
        if (conflicts != null)
            for (Placement conflict: conflicts)
                ret -= penalty(conflict);
        return ret;
    }
    
    @Override
    public double getValue(Collection<Lecture> variables) {
        double ret = 0;
        Set<RoomConstraint> constraints = new HashSet<RoomConstraint>();
        for (Lecture lect: variables) {
            if (lect.getAssignment() == null) continue;
            if (lect.getAssignment().isMultiRoom()) {
                for (RoomLocation r : lect.getAssignment().getRoomLocations()) {
                    if (r.getRoomConstraint() != null && constraints.add(r.getRoomConstraint()))
                        ret += penalty(r.getRoomConstraint());
                }
            } else if (lect.getAssignment().getRoomLocation().getRoomConstraint() != null && 
                    constraints.add(lect.getAssignment().getRoomLocation().getRoomConstraint())) {
                ret += penalty(lect.getAssignment().getRoomLocation().getRoomConstraint());
            }
        }
        return ret;
    }
    
    @Override
    protected double[] computeBounds() {
        return new double[] {
                ((TimetableModel)getModel()).getRoomConstraints().size() * Constants.SLOTS_PER_DAY_NO_EVENINGS * Constants.NR_DAYS_WEEK,
                0.0
        };
    }
    
    @Override
    public double[] getBounds(Collection<Lecture> variables) {
        Set<RoomConstraint> constraints = new HashSet<RoomConstraint>();
        for (Lecture lect: variables) {
            if (lect.getAssignment() == null) continue;
            if (lect.getAssignment().isMultiRoom()) {
                for (RoomLocation r : lect.getAssignment().getRoomLocations()) {
                    if (r.getRoomConstraint() != null)
                        constraints.add(r.getRoomConstraint());
                }
            } else if (lect.getAssignment().getRoomLocation().getRoomConstraint() != null) {
                constraints.add(lect.getAssignment().getRoomLocation().getRoomConstraint());
            }
        }
        return new double[] {
                constraints.size() * Constants.SLOTS_PER_DAY_NO_EVENINGS * Constants.NR_DAYS_WEEK,
                0.0 };
    }
    
    private static int sDaysMWF = Constants.DAY_CODES[0] + Constants.DAY_CODES[2] + Constants.DAY_CODES[4];
    private static int sDaysTTh = Constants.DAY_CODES[1] + Constants.DAY_CODES[3];

    /** Number of broken time patterns for this room */
    protected static int countUselessSlotsBrokenTimePatterns(RoomConstraint rc, TimeLocation time) {
        int ret = 0;
        int slot = time.getStartSlot() % Constants.SLOTS_PER_DAY;
        int days = time.getDayCode();
        if ((days & sDaysMWF) != 0 && (days & sDaysMWF) != sDaysMWF) {
            for (int s = slot; s < slot + time.getLength(); s++) {
                int nrEmpty = 0;
                if ((Constants.DAY_CODES[0] & days) == 0 && rc.getResource(0 * Constants.SLOTS_PER_DAY + s).isEmpty())
                    nrEmpty++;
                if ((Constants.DAY_CODES[2] & days) == 0 && rc.getResource(2 * Constants.SLOTS_PER_DAY + s).isEmpty())
                    nrEmpty++;
                if ((Constants.DAY_CODES[4] & days) == 0 && rc.getResource(4 * Constants.SLOTS_PER_DAY + s).isEmpty())
                    nrEmpty++;
                if (nrEmpty > 0)
                    ret ++;
            }
        }
        if ((days & sDaysTTh) != 0 && (days & sDaysTTh) != sDaysTTh) {
            for (int s = slot; s < slot + time.getLength(); s++) {
                int nrEmpty = 0;
                if ((Constants.DAY_CODES[1] & days) == 0 && rc.getResource(1 * Constants.SLOTS_PER_DAY + s).isEmpty())
                    nrEmpty++;
                if ((Constants.DAY_CODES[3] & days) == 0 && rc.getResource(3 * Constants.SLOTS_PER_DAY + s).isEmpty())
                    nrEmpty++;
                if (nrEmpty > 0)
                    ret ++;
            }
        }
        return ret / 6;
    }
    
    /** Number of useless slots for this room */
    public static int countUselessSlotsBrokenTimePatterns(RoomConstraint rc) {
        int ret = 0;
        for (int d = 0; d < Constants.NR_DAYS; d++) {
            for (int s = 0; s < Constants.SLOTS_PER_DAY; s++) {
                int slot = d * Constants.SLOTS_PER_DAY + s;
                if (rc.getResource(slot).isEmpty()) {
                    switch (d) {
                        case 0:
                            if (!rc.getResource(2 * Constants.SLOTS_PER_DAY + s).isEmpty()
                                    && !rc.getResource(4 * Constants.SLOTS_PER_DAY + s).isEmpty())
                                ret++;
                            break;
                        case 1:
                            if (!rc.getResource(3 * Constants.SLOTS_PER_DAY + s).isEmpty())
                                ret++;
                            break;
                        case 2:
                            if (!rc.getResource(0 * Constants.SLOTS_PER_DAY + s).isEmpty()
                                    && !rc.getResource(4 * Constants.SLOTS_PER_DAY + s).isEmpty())
                                ret++;
                            break;
                        case 3:
                            if (!rc.getResource(1 * Constants.SLOTS_PER_DAY + s).isEmpty())
                                ret++;
                            break;
                        case 4:
                            if (!rc.getResource(0 * Constants.SLOTS_PER_DAY + s).isEmpty()
                                    && !rc.getResource(2 * Constants.SLOTS_PER_DAY + s).isEmpty())
                                ret++;
                            break;
                    }
                }
            }
        }
        return Math.round((1.0f / 6.0f) * ret);
    }
}
