package ua.edu.teacherlicence.opp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgramStaffStats {

    /** Degree of the educational program (бакалавр/магістр/доктор філософії) */
    private String degree;

    /** Total unique teachers assigned to this program's disciplines */
    private int totalTeachers;

    /** Teachers with degree/title AND main employment */
    private int mainWithDegreeCount;
    private double mainWithDegreePercent;
    /** п.35: ≥ 50% */
    private boolean point35Compliant;

    /** Doctors of science or professors */
    private int doctorsOrProfessorsCount;
    private double doctorsOrProfessorsPercent;

    /** Teachers with degree/title + main employment + matching qualification */
    private int qualifiedMainCount;
    /** п.35 С: ≥ 3 */
    private boolean point35cCompliant;

    /** How many disciplines have all assigned teachers compliant with п.38 (≥4 types) */
    private int disciplinesTotal;
    private int disciplinesFullyStaffed;
    /** п.36: all disciplines are fully staffed */
    private boolean point36Compliant;
}
