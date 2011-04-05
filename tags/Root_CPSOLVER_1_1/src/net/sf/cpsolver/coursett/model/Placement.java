package net.sf.cpsolver.coursett.model;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.Vector;

import net.sf.cpsolver.coursett.Constants;
import net.sf.cpsolver.coursett.constraint.GroupConstraint;
import net.sf.cpsolver.coursett.constraint.InstructorConstraint;
import net.sf.cpsolver.coursett.constraint.SpreadConstraint;
import net.sf.cpsolver.coursett.preference.PreferenceCombination;
import net.sf.cpsolver.ifs.model.Value;
import net.sf.cpsolver.ifs.util.FastVector;
import net.sf.cpsolver.ifs.util.ToolBox;

/**
 * Placement (value).
 * <br><br>
 * It combines room and time location
 *
 * @version
 * CourseTT 1.1 (University Course Timetabling)<br>
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
 */

public class Placement extends Value {
    private TimeLocation iTimeLocation;
    private RoomLocation iRoomLocation;
    private Vector iRoomLocations = null;
    private Long iAssignmentId = null;
    
    private Integer iCacheTooBigRoomPreference = null;
    
    private int iHashCode = 0;
    
    /** Constructor
     * @param lecture lecture
     * @param timeLocation time location
     * @param roomLocation room location
     */
    public Placement(Lecture lecture, TimeLocation timeLocation, RoomLocation roomLocation) {
        super(lecture);
        iTimeLocation = timeLocation;
        iRoomLocation = roomLocation;
        if (iRoomLocation==null) {
        	iRoomLocations = new FastVector(0);
        }
        iHashCode = getName().hashCode();
    }
    public Placement(Lecture lecture, TimeLocation timeLocation, Vector roomLocations) {
    	super(lecture);
    	iTimeLocation = timeLocation;
    	iRoomLocation = (roomLocations.isEmpty()?null:(RoomLocation)roomLocations.firstElement());
    	if (roomLocations.size()!=1) {
    		iRoomLocations = new FastVector(roomLocations);
    	}
    	iHashCode = getName().hashCode();
    }
    
    /** Time location */
    public TimeLocation getTimeLocation() { return iTimeLocation; }
    /** Room location */
    public RoomLocation getRoomLocation() { return iRoomLocation; }
    /** Room locations (multi-room placement) */
    public Vector getRoomLocations() { return iRoomLocations; }
    public Vector getBuildingIds() {
    	if (isMultiRoom()) {
    		Vector ret = new Vector(iRoomLocations.size());
    		for (Enumeration e=iRoomLocations.elements();e.hasMoreElements();) {
    			RoomLocation r = (RoomLocation)e.nextElement();
    			ret.addElement(r.getBuildingId());
    		}
    		return ret;
    	} else {
    		Vector ret = new Vector(1);
    		ret.addElement(iRoomLocation.getBuildingId());
    		return ret;
    	}
    }
    public Vector getRoomIds() {
    	if (isMultiRoom()) {
        	Vector ret = new Vector(iRoomLocations.size());
        	for (Enumeration e=iRoomLocations.elements();e.hasMoreElements();) {
        		RoomLocation r = (RoomLocation)e.nextElement();
        		ret.addElement(r.getId());
        	}
        	return ret;
    	} else {
    		Vector ret = new Vector(1);
    		ret.addElement(iRoomLocation.getId());
    		return ret;
    	}
    }
    public Vector getRoomNames() {
    	if (isMultiRoom()) {
        	Vector ret = new Vector(iRoomLocations.size());
        	for (Enumeration e=iRoomLocations.elements();e.hasMoreElements();) {
        		RoomLocation r = (RoomLocation)e.nextElement();
        		ret.addElement(r.getName());
        	}
        	return ret;
    	} else {
    		Vector ret = new Vector(1);
    		if (iRoomLocation!=null)
    			ret.addElement(iRoomLocation.getName());
    		return ret;
    	}
    }    
    public Vector getRoomPrefs() {
    	if (isMultiRoom()) {
        	Vector ret = new Vector(iRoomLocations.size());
        	for (Enumeration e=iRoomLocations.elements();e.hasMoreElements();) {
        		RoomLocation r = (RoomLocation)e.nextElement();
        		ret.addElement(new Integer(r.getPreference()));
        	}
        	return ret;
    	} else {
    		Vector ret = new Vector(1);
    		if (iRoomLocation!=null)
    			ret.addElement(new Integer(iRoomLocation.getPreference()));
    		return ret;
    	}
    }    
    public boolean isMultiRoom() { return (iRoomLocations!=null && iRoomLocations.size()!=1); }
    public RoomLocation getRoomLocation(Long roomId) {
    	if (isMultiRoom()) {
    		for (Enumeration e=iRoomLocations.elements();e.hasMoreElements();) {
    			RoomLocation r = (RoomLocation)e.nextElement();
    			if (r.getId().equals(roomId)) return r;
    		}
    	} else if (iRoomLocation!=null && iRoomLocation.getId().equals(roomId)) return iRoomLocation;
    	return null; 
    }
    public boolean hasRoomLocation(Long roomId) {
    	if (isMultiRoom()) {
    		for (Enumeration e=iRoomLocations.elements();e.hasMoreElements();) {
    			RoomLocation r = (RoomLocation)e.nextElement();
    			if (r.getId().equals(roomId)) return true;
    		}
    		return false;
    	} else return iRoomLocation!=null && iRoomLocation.getId().equals(roomId); 
    }
    
    public String getRoomName(String delim) {
    	if (isMultiRoom()) {
    		StringBuffer sb = new StringBuffer();
    		for (Enumeration e=getRoomLocations().elements();e.hasMoreElements();) {
    			RoomLocation r = (RoomLocation)e.nextElement();
    			sb.append(r.getName());
    			if (e.hasMoreElements())
    				sb.append(delim);
    		}
    		return sb.toString();
    	} else {
    		return (getRoomLocation()==null?"":getRoomLocation().getName());
    	}
    }
    
    public String getName() { 
    	Lecture lecture = (Lecture)variable();
    	return getTimeLocation().getName()+" "+getRoomName(", ")+(lecture!=null && lecture.getInstructorName()!=null?" "+lecture.getInstructorName():""); 
    }
    
    public String getLongName() { 
    	Lecture lecture = (Lecture)variable();
    	if (isMultiRoom()) {
    		StringBuffer sb = new StringBuffer();
    		for (Enumeration e=getRoomLocations().elements();e.hasMoreElements();) {
    			RoomLocation r = (RoomLocation)e.nextElement();
    			sb.append(r.getName());
    			if (e.hasMoreElements())
    				sb.append(", ");
    		}
    		return getTimeLocation().getLongName()+" "+sb+(lecture.getInstructorName()!=null?" "+lecture.getInstructorName():"");
    	} else
    		return getTimeLocation().getLongName()+(getRoomLocation()==null?"":" "+getRoomLocation().getName())+(lecture.getInstructorName()!=null?" "+lecture.getInstructorName():""); 
    }

    public boolean sameRooms(Placement placement) {
        if (placement.isMultiRoom()!=isMultiRoom()) return false;
        if (isMultiRoom()) {
        	if (placement.getRoomLocations().size()!=getRoomLocations().size()) return false;
        	return placement.getRoomLocations().containsAll(getRoomLocations());
        } else {
        	if (placement.getRoomLocation()==null) return getRoomLocation()==null;
        	return placement.getRoomLocation().equals(getRoomLocation());
        }
    }
    public boolean shareRooms(Placement placement) {
    	if (isMultiRoom()) {
    		if (placement.isMultiRoom()) {
    			for (Enumeration e=getRoomLocations().elements();e.hasMoreElements();) {
    				RoomLocation rl = (RoomLocation)e.nextElement();
    				if (rl.getRoomConstraint()==null || !rl.getRoomConstraint().getConstraint()) continue;
    				if (placement.getRoomLocations().contains(rl)) return true;
    			} return false;
    		} else {
    			return getRoomLocations().contains(placement.getRoomLocation());
    		}
    	} else {
    		if (getRoomLocation().getRoomConstraint()==null || !getRoomLocation().getRoomConstraint().getConstraint()) return false;
    		if (placement.isMultiRoom()) {
    			return placement.getRoomLocations().contains(getRoomLocation());
    		} else {
    			return getRoomLocation().equals(placement.getRoomLocation());
    		}
    	}
    }
    public int nrDifferentRooms(Placement placement) {
    	if (isMultiRoom()) {
    		int ret = 0;
    		for (Enumeration e=getRoomLocations().elements();e.hasMoreElements();) {
    			RoomLocation r = (RoomLocation)e.nextElement();
    			if (!placement.getRoomLocations().contains(r))
    				ret++;
    		}
    		return ret;
    	} else {
    		return (placement.getRoomLocation().equals(getRoomLocation())?0:1);
    	}
    }
    public int nrDifferentBuildings(Placement placement) {
    	if (isMultiRoom()) {
    		int ret = 0;
    		for (Enumeration e=getRoomLocations().elements();e.hasMoreElements();) {
    			RoomLocation r = (RoomLocation)e.nextElement();
    			boolean contains = false;
    			for (Enumeration f=placement.getRoomLocations().elements();!contains && f.hasMoreElements();) {
    				RoomLocation q = (RoomLocation)f.nextElement();
    				if (ToolBox.equals(r.getBuildingId(),q.getBuildingId())) contains = true;
    			}
    			if (!contains) ret++;
    		}
    		return ret;
    	} else {
    		return (ToolBox.equals(placement.getRoomLocation().getBuildingId(),getRoomLocation().getBuildingId())?0:1);
    	}
    }    
    public int sumRoomPreference() {
    	if (isMultiRoom()) {
    		int ret = 0;
    		for (Enumeration e=getRoomLocations().elements();e.hasMoreElements();) {
    			RoomLocation r = (RoomLocation)e.nextElement();
    			ret += r.getPreference();
    		}
    		return ret;
    	} else {
    		return getRoomLocation().getPreference();
    	}
    }
    public int getRoomPreference() {
    	if (isMultiRoom()) {
    		PreferenceCombination p = PreferenceCombination.getDefault();
    		for (Enumeration e=getRoomLocations().elements();e.hasMoreElements();) {
    			RoomLocation r = (RoomLocation)e.nextElement();
    			p.addPreferenceInt(r.getPreference());
    		}
    		return p.getPreferenceInt();
    	} else {
    		return getRoomLocation().getPreference();
    	}
    }    
    public int getRoomSize() {
    	if (isMultiRoom()) {
    		int roomSize = 0;
    		for (Enumeration e=getRoomLocations().elements();e.hasMoreElements();) {
    			RoomLocation r = (RoomLocation)e.nextElement();
    			roomSize += r.getRoomSize();
    		}
    		return roomSize;
    	} else {
    		return getRoomLocation().getRoomSize();
    	}
    }
    public int minRoomSize() {
    	if (isMultiRoom()) {
    		if (getRoomLocations().isEmpty()) return 0;
    		int roomSize = Integer.MAX_VALUE;
    		for (Enumeration e=getRoomLocations().elements();e.hasMoreElements();) {
    			RoomLocation r = (RoomLocation)e.nextElement();
    			roomSize += Math.min(roomSize, r.getRoomSize());
    		}
    		return roomSize;
    	} else {
    		return getRoomLocation().getRoomSize();
    	}
    }
    
    public int getTooBigRoomPreference() {
    	if (iCacheTooBigRoomPreference!=null) return iCacheTooBigRoomPreference.intValue();
    	if (isMultiRoom()) {
    		PreferenceCombination pref = PreferenceCombination.getDefault(); 
    		for (Enumeration e=getRoomLocations().elements();e.hasMoreElements();) {
    			RoomLocation r = (RoomLocation)e.nextElement();
    			if (r.getRoomSize()>((Lecture)variable()).getStronglyDiscouragedRoomSize())
    				pref.addPreferenceInt(Constants.sPreferenceLevelStronglyDiscouraged);
    			else if (r.getRoomSize()>((Lecture)variable()).getDiscouragedRoomSize())
    				pref.addPreferenceInt(Constants.sPreferenceLevelDiscouraged);
    		}
    		iCacheTooBigRoomPreference = new Integer(pref.getPreferenceInt());
    		return iCacheTooBigRoomPreference.intValue();
    	} else {
    		if (getRoomLocation().getRoomSize()>((Lecture)variable()).getStronglyDiscouragedRoomSize())
    			iCacheTooBigRoomPreference = new Integer(Constants.sPreferenceLevelStronglyDiscouraged);
    		else if (getRoomLocation().getRoomSize()>((Lecture)variable()).getDiscouragedRoomSize())
    			iCacheTooBigRoomPreference = new Integer(Constants.sPreferenceLevelDiscouraged);
    		else
    			iCacheTooBigRoomPreference = new Integer(Constants.sPreferenceLevelNeutral);
    		return iCacheTooBigRoomPreference.intValue();
    	}
    }
    public int nrUselessHalfHours() {
    	if (isMultiRoom()) {
    		int ret = 0;
    		for (Enumeration e=getRoomLocations().elements();e.hasMoreElements();) {
    			RoomLocation r = (RoomLocation)e.nextElement();
    			if (r.getRoomConstraint()==null) continue;
    			ret += r.getRoomConstraint().countUselessSlots(this);
    		}
    		return ret;
    	} else {
    		return (getRoomLocation().getRoomConstraint()==null?0:getRoomLocation().getRoomConstraint().countUselessSlots(this));
    	}
    }
    
    public boolean isHard() {
    	if (Constants.sPreferenceProhibited.equals(Constants.preferenceLevel2preference(getTimeLocation().getPreference()))) return true;
    	if (getRoomLocation()!=null && Constants.sPreferenceProhibited.equals(Constants.preferenceLevel2preference(getRoomLocation().getPreference()))) return true;
    	Lecture lecture = (Lecture)variable();
    	for (Iterator i=lecture.hardGroupSoftConstraints().iterator();i.hasNext();) {
    		GroupConstraint gc = (GroupConstraint)i.next();
    		if (gc.isSatisfied()) continue;
    		if (Constants.sPreferenceProhibited.equals(gc.getPrologPreference())) return true;
    		if (Constants.sPreferenceRequired.equals(gc.getPrologPreference())) return true;
    	}
    	return false;    	
    }
    
    public boolean sameTime(Placement placement) {
    	return placement.getTimeLocation().equals(getTimeLocation());
    }

    public boolean equals(Object object) {
        if (object==null || !(object instanceof Placement)) return false;
        Placement placement = (Placement)object;
        if (placement.getId()==getId()) return true; //quick check
        Lecture lecture = (Lecture)placement.variable();
        Lecture thisLecture = (Lecture)variable();
        if (lecture!=null && thisLecture!=null && !lecture.getClassId().equals(thisLecture.getClassId())) return false;
        if (!sameRooms(placement)) return false;
        if (!sameTime(placement)) return false;
        return true;
    }
    
    public int hashCode() {
    	return iHashCode;
    }
    
    public String toString() {
        return variable().getName()+" "+getName();
    }

    /** Distance between two placements */
    public static double getDistance(Placement p1, Placement p2) {
    	if (p1.isMultiRoom()) {
    		if (p2.isMultiRoom()) {
    			double dist = 0.0;
    			for (Enumeration e1=p1.getRoomLocations().elements();e1.hasMoreElements();) {
    				RoomLocation r1 = (RoomLocation)e1.nextElement();
    				for (Enumeration e2=p2.getRoomLocations().elements();e2.hasMoreElements();) {
    					RoomLocation r2 = (RoomLocation)e2.nextElement();
    					dist = Math.max(dist,r1.getDistance(r2));
    				}
    			}
    			return dist;
    		} else {
    			if (p2.getRoomLocation()==null) return 0.0;
    			double dist = 0.0;
    			for (Enumeration e1=p1.getRoomLocations().elements();e1.hasMoreElements();) {
    				RoomLocation r1 = (RoomLocation)e1.nextElement();
    				dist = Math.max(dist,r1.getDistance(p2.getRoomLocation()));
    			}
    			return dist;
    		}
    	} else if (p2.isMultiRoom()) {
    		if (p1.getRoomLocation()==null) return 0.0;
			double dist = 0.0;
			for (Enumeration e2=p2.getRoomLocations().elements();e2.hasMoreElements();) {
				RoomLocation r2 = (RoomLocation)e2.nextElement();
				dist = Math.max(dist,p1.getRoomLocation().getDistance(r2));
			}
			return dist;
    	} else {
    		if (p1.getRoomLocation()==null || p2.getRoomLocation()==null) return 0.0;
    		return p1.getRoomLocation().getDistance(p2.getRoomLocation());
    	}
    }
    
    public int getCommitedConflicts() {
    	int ret = 0;
    	Lecture lecture = (Lecture)variable();
    	for (Iterator i1=lecture.students().iterator();i1.hasNext();) {
    		Student student = (Student)i1.next();
    		ret += student.countConflictPlacements(this);
    	}
    	return ret;
    }
    
    public Long getAssignmentId() { return iAssignmentId; }
    public void setAssignmentId(Long assignmentId) { iAssignmentId = assignmentId; }
    public boolean canShareRooms(Placement other) {
    	return ((Lecture)variable()).canShareRoom((Lecture)other.variable());
    }
    
    public boolean isValid() {
    	Lecture lecture = (Lecture)variable();
    	if (!lecture.isValid(this)) return false;
       	for (Enumeration e=lecture.getInstructorConstraints().elements();e.hasMoreElements();) {
       		InstructorConstraint ic = (InstructorConstraint)e.nextElement();
       		if (!ic.isAvailable(lecture, this)) return false;
       	}
    	if (lecture.getNrRooms()>0) {
    		if (isMultiRoom()) {
    			for (Enumeration e=getRoomLocations().elements();e.hasMoreElements();) {
    				RoomLocation roomLocation = (RoomLocation)e.nextElement();
    				if (roomLocation.getRoomConstraint()!=null && !roomLocation.getRoomConstraint().isAvailable(lecture, getTimeLocation(),lecture.getScheduler()))
    					return false;
    			}
    		} else {
				if (getRoomLocation().getRoomConstraint()!=null && !getRoomLocation().getRoomConstraint().isAvailable(lecture, getTimeLocation(),lecture.getScheduler()))
					return false;
    		}
    	}
    	return true;
    }
    
    public String getNotValidReason() {
    	Lecture lecture = (Lecture)variable();
    	String reason = lecture.getNotValidReason(this);
    	if (reason!=null) return reason;
       	for (Enumeration e=lecture.getInstructorConstraints().elements();e.hasMoreElements();) {
       		InstructorConstraint ic = (InstructorConstraint)e.nextElement();
       		if (!ic.isAvailable(lecture, this)) {
       			if (ic.isAvailable(lecture, getTimeLocation()))
       				return "instructor "+ic.getName()+" not available at "+getTimeLocation().getLongName();
       			else
       				return "placement "+getTimeLocation().getLongName()+" "+getRoomName(", ")+" is too far for instructor "+ic.getName();
       		}
    	}
    	if (lecture.getNrRooms()>0) {
    		if (isMultiRoom()) {
    			for (Enumeration e=getRoomLocations().elements();e.hasMoreElements();) {
    				RoomLocation roomLocation = (RoomLocation)e.nextElement();
    				if (roomLocation.getRoomConstraint()!=null && !roomLocation.getRoomConstraint().isAvailable(lecture, getTimeLocation(),lecture.getScheduler()))
    					return "room "+roomLocation.getName()+" not available at "+getTimeLocation().getLongName();
    			}
    		} else {
				if (getRoomLocation().getRoomConstraint()!=null && !getRoomLocation().getRoomConstraint().isAvailable(lecture, getTimeLocation(),lecture.getScheduler()))
					return "room "+getRoomLocation().getName()+" not available at "+getTimeLocation().getLongName();
    		}
    	}
    	return reason;
    }
    
    public int getNrRooms() {
    	if (iRoomLocations!=null) return iRoomLocations.size();
    	return (iRoomLocation==null?0:1);
    }
    
    public int getSpreadPenalty() {
    	int spread = 0;
    	for (Iterator i=((Lecture)variable()).getSpreadConstraints().iterator();i.hasNext();) {
    		SpreadConstraint sc = (SpreadConstraint)i.next();
    		spread += sc.getPenalty(this);
    	}
    	return spread;
    }

    public int getMaxSpreadPenalty() {
    	int spread = 0;
    	for (Iterator i=((Lecture)variable()).getSpreadConstraints().iterator();i.hasNext();) {
    		SpreadConstraint sc = (SpreadConstraint)i.next();
    		spread += sc.getMaxPenalty(this);
    	}
    	return spread;
    }
    
    public double toDouble() {
    	TimetableModel m = (TimetableModel)variable().getModel(); 
    	return m.getTimetableComparator().value(this, m.getPerturbationsCounter());
    }
    
    private transient Object iAssignment = null;
    public Object getAssignment() {
        return iAssignment;
    }
    public void setAssignment(Object assignment) {
        iAssignment = assignment;
    }
}