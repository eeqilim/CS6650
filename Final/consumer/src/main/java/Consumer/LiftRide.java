package Consumer;


import com.google.gson.annotations.SerializedName;
import java.util.Objects;

/**
 * consumer.LiftRide
 */
public class LiftRide {
  @SerializedName("time")
  private Integer time = null;

  @SerializedName("liftID")
  private Integer liftID = null;

  public LiftRide time(Integer time) {
    this.time = time;
    return this;
  }

  /**
   * Get time
   * @return time
   **/
  public Integer getTime() {
    return time;
  }

  public void setTime(Integer time) {
    this.time = time;
  }

  public LiftRide liftID(Integer liftID) {
    this.liftID = liftID;
    return this;
  }

  /**
   * Get liftID
   * @return liftID
   **/
  public Integer getLiftID() {
    return liftID;
  }

  public void setLiftID(Integer liftID) {
    this.liftID = liftID;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    LiftRide liftRide = (LiftRide) o;
    return Objects.equals(this.time, liftRide.time) &&
        Objects.equals(this.liftID, liftRide.liftID);
  }

  @Override
  public int hashCode() {
    return Objects.hash(time, liftID);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class consumer.LiftRide {\n");
    sb.append("    time: ").append(toIndentedString(time)).append("\n");
    sb.append("    liftID: ").append(toIndentedString(liftID)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}