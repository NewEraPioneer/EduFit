package vn.edufit.connection.application.policy;

import java.util.List;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.exception.ErrorCode;

/** Writer validation only: readers retain stored strings verbatim. */
@Component
public class ConnectionRequestInput {
  private static final Pattern CANONICAL = Pattern.compile(
      "(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY) ([01][0-9]|2[0-3]):([0-5][0-9])-([01][0-9]|2[0-3]):([0-5][0-9])");
  private record Interval(DayOfWeek day,int start,int end) {}
  private final String mode;
  public ConnectionRequestInput(@Value("${edufit.connection.slots.validation-mode:legacy-compatible}") String mode) {
    if (!"legacy-compatible".equals(mode) && !"strict-v1".equals(mode))
      throw new IllegalArgumentException("Unknown connection slot validation mode: " + mode);
    this.mode=mode;
  }
  public void validate(List<String> slots,String message) {
    if(message!=null && message.length()>500) fail("Lời nhắn tối đa 500 ký tự");
    if(slots==null || slots.isEmpty() || slots.stream().anyMatch(s -> s==null || s.isBlank() || s.length()>100))
      fail("Cần chọn ít nhất một khung giờ");
    if (!"strict-v1".equals(mode)) return;
    var intervals=new ArrayList<Interval>();
    for(String slot:slots) {
      var match=CANONICAL.matcher(slot);
      if(!match.matches()) fail("Khung giờ phải có dạng MONDAY 18:00-19:00");
      int start=Integer.parseInt(match.group(2))*60+Integer.parseInt(match.group(3));
      int end=Integer.parseInt(match.group(4))*60+Integer.parseInt(match.group(5));
      if(start>=end) fail("Khung giờ phải bắt đầu trước khi kết thúc trong cùng ngày");
      intervals.add(new Interval(DayOfWeek.valueOf(match.group(1)),start,end));
    }
    intervals.sort(Comparator.comparing(Interval::day).thenComparingInt(Interval::start));
    Interval previous=null;
    for(Interval interval:intervals) {
      if(previous!=null && previous.day()==interval.day() && interval.start()<previous.end())
        fail("Khung giờ không được trùng hoặc chồng lấn");
      previous=interval;
    }
  }
  private static void fail(String message) { throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED,message); }
}
