package ICLMobileSimulation;

public class MasterCameraInfo {
    public int CameraId;
    public String CameraName;
    public int AgencyId;
    public String AgencyName;
    public double Altitude;
    public double Latitude;
    public double Longitude;
    public int PostalCode;
    public String Region;
    public String LocationDescriptor1;
    public String LocationDescriptor2;
    public CameraType CameraType;
    public boolean CanControl;
    public String CameraGroup;
    public AccessRight AccessRight;

    public MasterCameraInfo() {
    }

    public String toString() {
        return "Id:" + CameraId + ", Name:" + CameraName + ", Agency:" + AgencyName + ", Location:" + PostalCode;
    }
}
