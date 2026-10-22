package uz.uruktype.android;

/** Pure geometry for the hold-and-slide punctuation picker. */
final class PunctuationSwipe {
  static int choose(float x,float y,float startX,float startY,
      int left,int top,int width,int height,int anchorHeight,int slop){
    // Releasing without a deliberate slide cancels, rather than inserting a sign.
    if(Math.hypot(x-startX,y-startY)<=slop)return -1;
    // Permit horizontal sliding on the dot row and sliding upward into the panel.
    if(x<left-slop||x>left+width+slop||y<top-slop
        ||y>top+height+anchorHeight+slop)return -1;
    return x<left+width/2f?0:1;
  }
}
