(
// s.options.outDevice = "Speaker/HP (Realtek High Defini";
s.waitForBoot{
	"ready".postln;
	o=SeamlessLoopRecorder.new(Server.default);
	s.sync;
	o.record(2,0.1,{ arg buf;
		b=buf;
	});		
})

b.plot