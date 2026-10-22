package uz.uruktype.android;

public final class PunctuationSwipeCheck {
  private static void expect(int expected,float x,float y,String scenario){
    int actual=PunctuationSwipe.choose(x,y,200,270,160,190,80,50,45,8);
    if(actual!=expected)throw new AssertionError(scenario+": expected "+expected+", got "+actual);
  }
  public static void main(String[] args){
    expect(-1,200,270,"hold and release without moving");
    expect(-1,203,272,"small finger jitter");
    expect(0,180,270,"slide left along dot row");
    expect(1,220,270,"slide right along dot row");
    expect(0,180,210,"slide upward onto question mark");
    expect(1,220,210,"slide upward onto comma");
    expect(-1,100,210,"cancel outside left edge");
    expect(-1,300,210,"cancel outside right edge");
    expect(-1,180,170,"cancel above picker");
    expect(-1,180,310,"cancel below keyboard");
    System.out.println("Punctuation gesture: all 10 scenarios passed.");
  }
}
