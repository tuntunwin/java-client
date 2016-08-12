package ICLMobileSimulation;

public class HttpResponseMessage {
	public int StatusCode;
    public String StatusDetail;

    @Override
    public String toString() {
        return "HttpResponseMessage => StatusCode: " + StatusCode + ", StatusDetail: " + StatusDetail;
    }

}
