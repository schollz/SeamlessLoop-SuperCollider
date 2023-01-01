(
// s.options.outDevice = "Speaker/HP (Realtek High Defini";
s=Server.local;
s.options.numOutputBusChannels=2;
s.options.numInputBusChannels=2;
s.waitForBoot{
	"ready".postln;
	o=SeamlessLoop.new(Server.default);
	s.sync;
	// // set various aspects
	o.set("lpf",10000);
	o.set("res",0.707);
	b=Array.newClear(3);
	3.do({ arg x;
		o.record(x*2+1,0.1,{ arg buf;b[x]=buf;});
	});
}
)

b[0].plot;
b[1].plot;
b[2].plot;

// play a loop
s.scope
o.play(0,0,b[0]);
o.play(0,0,b[1]);