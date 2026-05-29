package ua.edu.teacherlicence.scopus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScopusVerificationResult {

    /** Article found in Scopus */
    private boolean found;

    /** The teacher confirmed as author of this article */
    private boolean authorConfirmed;

    /** Scopus article ID (EID) if found */
    private String scopusId;

    /** The author name that matched from Scopus response */
    private String matchedAuthorName;

    /** Which search method found the result: DOI, AU-ID, AUTHLASTNAME */
    private String searchMethod;

    /** Scopus article title (for verification) */
    private String scopusTitle;

    /** Error message if API call failed */
    private String error;

    public static ScopusVerificationResult notFound() {
        return ScopusVerificationResult.builder().found(false).authorConfirmed(false).build();
    }

    public static ScopusVerificationResult error(String message) {
        return ScopusVerificationResult.builder().found(false).authorConfirmed(false).error(message).build();
    }

    /** True only when both article found AND author confirmed */
    public boolean isConfirmed() {
        return found && authorConfirmed;
    }
}
