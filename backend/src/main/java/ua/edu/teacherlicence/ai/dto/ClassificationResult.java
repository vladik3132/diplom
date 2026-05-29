package ua.edu.teacherlicence.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassificationResult {
    private int type;
    private double confidence;
    private String reasoning;
}
