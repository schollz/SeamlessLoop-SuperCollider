SeamlessLoopRecorder {
	var server;
	var fnXFader;
	var busInput;
	var synInput;

	*new {
		arg argServer;
		^super.new.init(argServer);
	}

	init {
		arg argServer;
		server=argServer;
		busInput = Bus.audio(server,2);

		SynthDef("defInput",{
			arg busOut, lpf=18000, res=1.0;
			var snd = SoundIn.ar([0,1]);
			snd = RLPF.ar(snd,lpf,res);
			Out.ar(busOut,snd);
		}).send(server);

		SynthDef("defRecord",{
			arg bufnum, busIn, recLevel=1.0, preLevel=0.0,t_trig=0,run=0,loop=1;
			RecordBuf.ar(
				inputArray: In.ar(busIn,2).poll,
				bufnum:bufnum,
				recLevel:recLevel,
				preLevel:preLevel,
				run:1,
				trigger:1,
				loop:0,
				doneAction:2,
			);
		}).send(server);

		server.sync;
		synInput = Synth.new("defInput",[\busOut,busInput],server,\addToHead);

		// https://fredrikolofsson.com/f0blog/buffer-xfader/
		fnXFader ={|inBuffer, duration= 2, curve= -2, action|
			var frames= duration*inBuffer.sampleRate;
			if(frames>inBuffer.numFrames, {
				"xfader: crossfade duration longer than half buffer - clipped.".warn;
			});
			frames= frames.min(inBuffer.numFrames.div(2)).round.asInteger;
			Buffer.alloc(inBuffer.server, inBuffer.numFrames-frames, inBuffer.numChannels, {|outBuffer|
				inBuffer.loadToFloatArray(action:{|arr|
					var interleavedFrames= frames*inBuffer.numChannels;
					var startArr= arr.copyRange(0, interleavedFrames-1);
					var endArr= arr.copyRange(arr.size-interleavedFrames, arr.size-1);
					var result= arr.copyRange(0, arr.size-1-interleavedFrames);
					interleavedFrames.do{|i|
						var fadeIn= i.lincurve(0, interleavedFrames-1, 0, 1, curve);
						var fadeOut= i.lincurve(0, interleavedFrames-1, 1, 0, 0-curve);
						result[i]= (startArr[i]*fadeIn)+(endArr[i]*fadeOut);
					};
					outBuffer.loadCollection(result, 0, action);
				});
			});
		};

	}

	// set changes the input parameters
	set {
		arg k,v;
		synInput.set(k,v);
	}

	// record makes a new recording and callsback the result
	record {
		arg seconds,xfade,action;
		Buffer.alloc(server,server.sampleRate*(seconds+xfade),2,{ arg buf1;
			"recording primed".postln;
			Synth.after(synInput,"defRecord",[\busIn,busInput]).onFree({
				arg syn;
				["recorded",buf1].postln;
				if (xfade>0,{
					fnXFader.value(buf1,xfade,-2,action:{
						arg buf2;
						("done with buffer"+buf1+"and made"+buf2).postln;
						action.value(buf2);
						buf1.free;
					});
				},{
					action.value(buf1);
				});
			});
		});
	}

	free {
		synInput.free;
		busInput.free;
	}
}