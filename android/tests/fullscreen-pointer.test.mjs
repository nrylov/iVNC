/* Whole-screen gestures must separate remote movement/scrolling from local pinch zoom. */
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {TrackpadModel} from '../app/src/main/assets/trackpad-model.js';
import {FullscreenPointer} from '../app/src/main/assets/fullscreen-pointer.js';
function fixture() {
    const sends=[], scrolls=[], pinches=[];
    const model=new TrackpadModel({send:(...v)=>sends.push(v),scroll:(...v)=>scrolls.push(v),bounds:()=>({width:1920,height:1080})});
    return {gesture:new FullscreenPointer(model,event=>pinches.push(event)),sends,scrolls,pinches};
}
test('single finger moves relatively and taps click at the cursor',()=>{
    const f=fixture();f.gesture.down(1,10,10,0);f.gesture.move(1,50,20);f.gesture.up(1,100);
    assert.equal(f.sends.length,1);assert.equal(f.sends[0][0],1000);
    f.gesture.down(1,60,20,200);f.gesture.up(1,250);
    assert.deepEqual(f.sends.slice(-2).map(v=>v[2]),[1,0]);
});
test('parallel two-finger motion scrolls without cursor movement or pinch',()=>{
    const f=fixture();f.gesture.down(1,0,0,0);f.gesture.down(2,100,0,1);
    f.gesture.move(1,0,20);f.gesture.move(2,100,20);f.gesture.up(1,200);f.gesture.up(2,210);
    assert.ok(f.scrolls.length>0);assert.equal(f.pinches.length,0);assert.equal(f.sends.length,0);
});
test('pinching zooms without remote scrolling, cursor movement or click',()=>{
    const f=fixture();f.gesture.down(1,0,0,0);f.gesture.down(2,100,0,1);
    f.gesture.move(2,140,0);f.gesture.up(1,150);f.gesture.up(2,160);
    assert.deepEqual(f.pinches.map(v=>v.phase),['start','move']);assert.equal(f.scrolls.length,0);assert.equal(f.sends.length,0);
});
test('a short two-finger tap right-clicks, while cancellation clicks nothing',()=>{
    const f=fixture();f.gesture.down(1,0,0,0);f.gesture.down(2,100,0,1);f.gesture.up(1,60);f.gesture.up(2,70);
    assert.deepEqual(f.sends.map(v=>v[2]),[4,0]);
    f.gesture.down(1,0,0,100);f.gesture.cancel();f.gesture.up(1,150);assert.equal(f.sends.length,2);
});
