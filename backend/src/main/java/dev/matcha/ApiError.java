package dev.matcha;

public final class ApiError extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public final int status;
    public final String code;

    public ApiError(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiError invalid(String message) { return new ApiError(400, "invalid_input", message); }
    public static ApiError forbidden(String message) { return new ApiError(403, "forbidden", message); }
    public static ApiError missing() { return new ApiError(404, "not_found", "This item is unavailable."); }
}
