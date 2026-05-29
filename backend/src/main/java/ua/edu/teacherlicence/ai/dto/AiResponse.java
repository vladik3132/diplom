package ua.edu.teacherlicence.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiResponse {
    private String response;
    private String model;
    private boolean success;
    private String error;

    public static AiResponse success(String response, String model) {
        return new AiResponse(response, model, true, null);
    }

    public static AiResponse error(String error) {
        return new AiResponse(null, null, false, error);
    }
}
