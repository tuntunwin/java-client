package ICLMobileSimulation;

public class VideoInfo {
	public int CameraId;
    public String CameraName;
    public String StreamUrl;
    public Boolean Success;
    public String Error;
    public String RebroLiveStreamId;

    public VideoInfo() {
        this.StreamUrl = null;
        this.Success = false;
    }

}
