SeamlessLoop {
	var server;
	var fnXFader;
	var buses;
	var syns;

	*new {
		arg argServer;
		^super.new.init(argServer);
	}

	init {
		arg argServer;
		server=argServer;

		buses = Dictionary.new();
		syns = Dictionary.new();

		buses.put("input",Bus.audio(server,2));
		buses.put("phase",Bus.audio(server,1));

		SynthDef("defPhase",{
			arg busOut;
			Out.ar(busOut,Phasor.ar(1,1,0,1728000000));
		}).send(server);

		SynthDef("defPlay",{
			arg out,buf,busPhase;
			var snd = BufRd.ar(
				numChannels:2,
				bufnum: buf,
				phase: In.ar(busPhase,1).mod(BufSamples.ir(buf)),
				interpolation:4,
			);
			Out.ar(out,snd);
		}).send(server);

		SynthDef("defInput",{
			arg busOut, lpf=18000, res=1.0;
			var snd = SoundIn.ar([0,1]);
			snd = RLPF.ar(snd,lpf,res);
			snd = LeakDC.ar(snd);
			Out.ar(busOut,snd);
		}).send(server);

		SynthDef("defRecord",{
			arg bufnum, busIn, recLevel=1.0, preLevel=0.0,t_trig=0,run=0,loop=1;
			RecordBuf.ar(
				inputArray: In.ar(busIn,2),
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
		syns.put("input",Synth.new("defInput",[\busOut,buses.at("input")],server,\addToHead));
		syns.put("phase",Synth.new("defPhase",[\busOut,buses.at("phase")]),server,\addToHead);

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
		syns.at("input").set(k,v);
		("[SeamlessLoopRecorder] set "++k++"="++v).postln;
	}

	// play will play a seamless loop
	play {
		arg name,out,buf;
		if (syns.at(name).notNil,{
			if (syns.at(name).isRunning,{
				syns.at(name).free;
			});
		});
		syns.put(name,Synth.after(syns.at("phase"),"defPlay",[\out,out,\buf,buf,\busPhase,buses.at("phase")]));
		NodeWatcher.register(syns.at(name));
	}

	// record makes a new recording and callsback the result
	record {
		arg seconds,xfade,action;
		Buffer.alloc(server,server.sampleRate*(seconds+xfade),2,{ arg buf1;
			("[SeamlessLoopRecorder] initiated for "++seconds++" s with "++xfade++" s of xfade").postln;
			Synth.after(syns.at("input"),"defRecord",[\busIn,buses.at("input"),\bufnum,buf1]).onFree({
				arg syn;
				if (xfade>0,{
					fnXFader.value(buf1,xfade,-2,action:{
						arg buf2;
						("[SeamlessLoopRecorder] finished xfaded "++seconds++" s recording").postln;
						action.value(buf2);
						buf1.free;
					});
				},{
					("[SeamlessLoopRecorder] finished "++seconds++" s recording").postln;
					action.value(buf1);
				});
			});
		});
	}

	free {
		syns.keysValuesDo({ arg buf, val;
			val.free;
		});
		buses.keysValuesDo({ arg buf, val;
			val.free;
		});
	}
}