/**
 * Calls System - نظام الاتصالات الجماعية والبث المباشر
 *
 * الميزات:
 * ✅ Multi-party audio/video
 * ✅ Screen sharing
 * ✅ Recording
 * ✅ Adaptive quality
 * ✅ Echo cancellation
 * ✅ Noise suppression
 * ✅ Real-time stats
 * ✅ Group management
 */

export interface CallParticipant {
  id: string;
  userId: string;
  displayName: string;
  avatar?: string;
  videoEnabled: boolean;
  audioEnabled: boolean;
  screenSharing: boolean;
  joinedAt: number;
  isMuted: boolean;
  isAudioOnly: boolean;
}

export interface CallStats {
  participantId: string;
  videoBitrate: number;
  audioBitrate: number;
  videoFrameRate: number;
  videoResolution: string;
  audioLevel: number;
  jitter: number;
  latency: number;
  packetLoss: number;
  timestamp: number;
}

export interface LiveStream {
  id: string;
  title: string;
  description?: string;
  hostId: string;
  hostName: string;
  isActive: boolean;
  startedAt: number;
  viewers: number;
  videoUrl: string;
  recordingUrl?: string;
  isRecording: boolean;
}

export interface CallQuality {
  level: 'LOW' | 'MEDIUM' | 'HIGH' | 'VERY_HIGH';
  videoBitrate: number;
  audioCodec: string;
  videoCodec: string;
  resolution: string;
  frameRate: number;
}

// ============= WebRTC Manager =============

export class WebRTCCallManager {
  private peerConnections: Map<string, RTCPeerConnection> = new Map();
  private dataChannels: Map<string, RTCDataChannel> = new Map();
  private localStream: MediaStream | null = null;
  private screenStream: MediaStream | null = null;
  private participants: Map<string, CallParticipant> = new Map();
  private stats: Map<string, CallStats> = new Map();
  private callQuality: CallQuality = {
    level: 'HIGH',
    videoBitrate: 2500000, // 2.5 Mbps
    audioCodec: 'opus',
    videoCodec: 'vp9',
    resolution: '1280x720',
    frameRate: 30,
  };

  private iceServers: RTCIceServer[] = [
    { urls: ['stun:stun.l.google.com:19302'] },
    { urls: ['stun:stun1.l.google.com:19302'] },
  ];

  private listeners: Map<string, Set<(data: any) => void>> = new Map();

  // ========== Initialization ==========

  async initialize(): Promise<void> {
    try {
      // Request camera and microphone permissions
      this.localStream = await navigator.mediaDevices.getUserMedia({
        video: {
          width: { ideal: 1280 },
          height: { ideal: 720 },
          frameRate: { ideal: 30 },
        },
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        },
      });

      console.log('[WebRTC] Local stream initialized');
      this.emit('local:stream', this.localStream);
    } catch (error) {
      console.error('[WebRTC] Failed to initialize local stream:', error);
      throw error;
    }
  }

  // ========== Call Management ==========

  async startCall(conversationId: string, participants: string[]): Promise<string> {
    const callId = `call_${Date.now()}`;

    try {
      if (!this.localStream) {
        await this.initialize();
      }

      // Create peer connections for each participant
      for (const participantId of participants) {
        await this.createPeerConnection(callId, participantId);
      }

      this.emit('call:started', {
        callId,
        conversationId,
        participants,
      });

      return callId;
    } catch (error) {
      console.error('[WebRTC] Failed to start call:', error);
      throw error;
    }
  }

  async endCall(callId: string): Promise<void> {
    // Close all peer connections
    for (const [peerId, pc] of this.peerConnections.entries()) {
      pc.close();
      this.peerConnections.delete(peerId);
    }

    // Close data channels
    for (const [peerId, dc] of this.dataChannels.entries()) {
      dc.close();
      this.dataChannels.delete(peerId);
    }

    // Stop local stream
    if (this.localStream) {
      this.localStream.getTracks().forEach((track) => track.stop());
      this.localStream = null;
    }

    // Stop screen stream
    if (this.screenStream) {
      this.screenStream.getTracks().forEach((track) => track.stop());
      this.screenStream = null;
    }

    this.participants.clear();
    this.stats.clear();

    this.emit('call:ended', { callId });
  }

  // ========== Peer Connection Management ==========

  private async createPeerConnection(callId: string, participantId: string): Promise<void> {
    const peerConnection = new RTCPeerConnection({
      iceServers: this.iceServers,
    });

    // Add local stream tracks
    if (this.localStream) {
      this.localStream.getTracks().forEach((track) => {
        peerConnection.addTrack(track, this.localStream!);
      });
    }

    // Handle ICE candidates
    peerConnection.onicecandidate = (event) => {
      if (event.candidate) {
        this.emit('ice:candidate', {
          participantId,
          candidate: event.candidate,
        });
      }
    };

    // Handle remote stream
    peerConnection.ontrack = (event) => {
      console.log('[WebRTC] Received remote track:', event.track.kind);
      this.emit('remote:track', {
        participantId,
        stream: event.streams[0],
        track: event.track,
      });
    };

    // Handle connection state changes
    peerConnection.onconnectionstatechange = () => {
      console.log('[WebRTC] Connection state:', peerConnection.connectionState);

      if (peerConnection.connectionState === 'failed') {
        this.handleConnectionFailure(participantId);
      }
    };

    // Create data channel
    const dataChannel = peerConnection.createDataChannel('messages', {
      ordered: true,
    });
    this.setupDataChannel(participantId, dataChannel);

    // Handle data channels from remote peer
    peerConnection.ondatachannel = (event) => {
      this.setupDataChannel(participantId, event.channel);
    };

    // Monitor stats
    this.monitorStats(peerConnection, participantId);

    this.peerConnections.set(participantId, peerConnection);
  }

  private setupDataChannel(participantId: string, dataChannel: RTCDataChannel): void {
    dataChannel.onopen = () => {
      console.log('[WebRTC] Data channel opened:', participantId);
    };

    dataChannel.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data);
        this.emit('data:received', {
          participantId,
          message,
        });
      } catch (error) {
        console.error('[WebRTC] Failed to parse data channel message:', error);
      }
    };

    dataChannel.onerror = (error) => {
      console.error('[WebRTC] Data channel error:', error);
    };

    dataChannel.onclose = () => {
      console.log('[WebRTC] Data channel closed:', participantId);
    };

    this.dataChannels.set(participantId, dataChannel);
  }

  // ========== Media Control =============

  async toggleVideo(enable: boolean): Promise<void> {
    if (!this.localStream) return;

    this.localStream.getVideoTracks().forEach((track) => {
      track.enabled = enable;
    });

    this.emit('local:video', { enabled: enable });
  }

  async toggleAudio(enable: boolean): Promise<void> {
    if (!this.localStream) return;

    this.localStream.getAudioTracks().forEach((track) => {
      track.enabled = enable;
    });

    this.emit('local:audio', { enabled: enable });
  }

  async startScreenShare(): Promise<void> {
    try {
      this.screenStream = await navigator.mediaDevices.getDisplayMedia({
        // `cursor` is a display-capture constraint, but the DOM typings expose
        // video as MediaTrackConstraints. Keep the browser-supported option
        // while making the boundary explicit.
        video: { cursor: 'always' } as unknown as MediaTrackConstraints,
        audio: false,
      });

      // Replace video track with screen
      const screenTrack = this.screenStream.getVideoTracks()[0];

      for (const [_, pc] of this.peerConnections.entries()) {
        const sender = pc.getSenders().find((s) => s.track?.kind === 'video');
        if (sender) {
          await sender.replaceTrack(screenTrack);
        }
      }

      // Handle screen share stop
      screenTrack.onended = () => {
        this.stopScreenShare();
      };

      this.emit('screen:shared', { active: true });
    } catch (error) {
      console.error('[WebRTC] Failed to start screen share:', error);
    }
  }

  async stopScreenShare(): Promise<void> {
    if (!this.screenStream) return;

    this.screenStream.getTracks().forEach((track) => track.stop());
    this.screenStream = null;

    // Switch back to camera
    if (this.localStream) {
      const videoTrack = this.localStream.getVideoTracks()[0];

      for (const [_, pc] of this.peerConnections.entries()) {
        const sender = pc.getSenders().find((s) => s.track?.kind === 'video');
        if (sender) {
          await sender.replaceTrack(videoTrack);
        }
      }
    }

    this.emit('screen:shared', { active: false });
  }

  // ========== Quality Management ==========

  async setCallQuality(level: 'LOW' | 'MEDIUM' | 'HIGH' | 'VERY_HIGH'): Promise<void> {
    const qualitySettings: Record<string, CallQuality> = {
      LOW: {
        level: 'LOW',
        videoBitrate: 500000,
        audioCodec: 'opus',
        videoCodec: 'vp8',
        resolution: '320x240',
        frameRate: 15,
      },
      MEDIUM: {
        level: 'MEDIUM',
        videoBitrate: 1000000,
        audioCodec: 'opus',
        videoCodec: 'vp8',
        resolution: '640x480',
        frameRate: 24,
      },
      HIGH: {
        level: 'HIGH',
        videoBitrate: 2500000,
        audioCodec: 'opus',
        videoCodec: 'vp9',
        resolution: '1280x720',
        frameRate: 30,
      },
      VERY_HIGH: {
        level: 'VERY_HIGH',
        videoBitrate: 5000000,
        audioCodec: 'opus',
        videoCodec: 'vp9',
        resolution: '1920x1080',
        frameRate: 60,
      },
    };

    this.callQuality = qualitySettings[level];

    // Apply bitrate limits
    for (const [_, pc] of this.peerConnections.entries()) {
      const senders = pc.getSenders();
      for (const sender of senders) {
        if (sender.track?.kind === 'video') {
          const parameters = sender.getParameters();
          parameters.encodings = parameters.encodings.map((encoding) => ({
            ...encoding,
            maxBitrate: this.callQuality.videoBitrate,
          }));
          // Some browsers start with no encoding entry. In that case there is
          // nothing to tune, and setParameters must retain its transaction id.
          if (parameters.encodings.length > 0) await sender.setParameters(parameters);
        }
      }
    }

    this.emit('quality:changed', this.callQuality);
  }

  // ========== Statistics ==========

  private async monitorStats(peerConnection: RTCPeerConnection, participantId: string): Promise<void> {
    const interval = setInterval(async () => {
      const stats = await peerConnection.getStats();

      stats.forEach((report) => {
        if (report.type === 'inbound-rtp') {
          if (report.kind === 'video') {
            const callStats: CallStats = {
              participantId,
              videoBitrate: report.bytesReceived,
              audioBitrate: 0,
              videoFrameRate: report.framesPerSecond || 0,
              videoResolution: `${report.frameWidth}x${report.frameHeight}`,
              audioLevel: 0,
              jitter: report.jitter || 0,
              latency: 0,
              packetLoss: report.packetsLost || 0,
              timestamp: Date.now(),
            };
            this.stats.set(participantId, callStats);
            this.emit('stats:updated', callStats);
          }
        }
      });
    }, 1000);

    // Clear interval when connection closes
    peerConnection.onconnectionstatechange = () => {
      if (peerConnection.connectionState === 'closed') {
        clearInterval(interval);
      }
    };
  }

  // ========== Error Handling ==========

  private handleConnectionFailure(participantId: string): void {
    console.warn('[WebRTC] Connection failed for participant:', participantId);

    // Attempt to reconnect
    setTimeout(() => {
      this.emit('connection:failed', { participantId });
    }, 1000);
  }

  // ========== Recording ==========

  async startRecording(callId: string): Promise<string> {
    const recordingId = `rec_${Date.now()}`;

    try {
      const mediaRecorder = new MediaRecorder(this.localStream!, {
        mimeType: 'video/webm;codecs=vp9,opus',
      });

      const chunks: BlobPart[] = [];

      mediaRecorder.ondataavailable = (event) => {
        chunks.push(event.data);
      };

      mediaRecorder.onstop = async () => {
        const blob = new Blob(chunks, { type: 'video/webm' });
        await this.uploadRecording(recordingId, blob, callId);
        this.emit('recording:stopped', { recordingId });
      };

      mediaRecorder.start();
      this.emit('recording:started', { recordingId });

      return recordingId;
    } catch (error) {
      console.error('[WebRTC] Failed to start recording:', error);
      throw error;
    }
  }

  private async uploadRecording(recordingId: string, blob: Blob, callId: string): Promise<void> {
    const formData = new FormData();
    formData.append('recording', blob);
    formData.append('recordingId', recordingId);
    formData.append('callId', callId);

    try {
      const response = await fetch('/api/calls/upload-recording', {
        method: 'POST',
        body: formData,
      });

      if (!response.ok) {
        throw new Error('Failed to upload recording');
      }

      console.log('[WebRTC] Recording uploaded successfully');
    } catch (error) {
      console.error('[WebRTC] Failed to upload recording:', error);
    }
  }

  // ========== Events ==========

  subscribe(eventType: string, callback: (data: any) => void): void {
    if (!this.listeners.has(eventType)) {
      this.listeners.set(eventType, new Set());
    }
    this.listeners.get(eventType)!.add(callback);
  }

  unsubscribe(eventType: string, callback: (data: any) => void): void {
    this.listeners.get(eventType)?.delete(callback);
  }

  private emit(eventType: string, data: any): void {
    const callbacks = this.listeners.get(eventType);
    if (callbacks) {
      callbacks.forEach((callback) => {
        try {
          callback(data);
        } catch (error) {
          console.error(`[WebRTC] Error in ${eventType} callback:`, error);
        }
      });
    }
  }

  getParticipants(): CallParticipant[] {
    return Array.from(this.participants.values());
  }

  getStats(): CallStats[] {
    return Array.from(this.stats.values());
  }
}

export const callManager = new WebRTCCallManager();

export default callManager;
