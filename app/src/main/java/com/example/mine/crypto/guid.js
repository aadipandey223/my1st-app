import React, { useState, useEffect, useRef } from 'react';
import { Wifi, WifiOff, Settings, QrCode, MessageCircle, Check, X, RefreshCw, Smartphone, Router, Key, Timer, Send, Bluetooth } from 'lucide-react';

// Crypto utility functions (simplified for demo)
const generateKeyPair = () => {
  const privateKey = Math.random().toString(36).substring(2, 34);
  const publicKey = Math.random().toString(36).substring(2, 34);
  return { privateKey, publicKey };
};

const generateQRData = (publicKey, deviceId) => {
  return `fusion:${publicKey}:${deviceId}:${Date.now()}`;
};

const FusionNodeApp = () => {
  const [currentScreen, setCurrentScreen] = useState('start');
  const [connectionState, setConnectionState] = useState('disconnected');
  const [connectionType, setConnectionType] = useState(null);
  const [availableNodes, setAvailableNodes] = useState([
    { id: 'AADI_pa', name: 'AADI Fusion Node PA', signal: 85, status: 'available', type: 'bluetooth' },
    { id: 'NODE_2B', name: 'Fusion Node 2B', signal: 72, status: 'available', type: 'wifi' },
    { id: 'RELAY_01', name: 'Relay Node 01', signal: 91, status: 'available', type: 'bluetooth' }
  ]);
  const [bluetoothDevices, setBluetoothDevices] = useState([
    { id: 'BT_001', name: 'AADI Fusion BT', signal: 88, paired: false },
    { id: 'BT_002', name: 'Relay Node BT', signal: 75, paired: true }
  ]);
  const [wifiNetworks, setWifiNetworks] = useState([
    { id: 'WIFI_001', name: 'FusionNet_5G', signal: 92, secured: true },
    { id: 'WIFI_002', name: 'AADI_Network', signal: 67, secured: true }
  ]);
  const [selectedNode, setSelectedNode] = useState(null);
  const [connectedDevice, setConnectedDevice] = useState(null);
  const [keyPair, setKeyPair] = useState(null);
  const [qrCode, setQrCode] = useState(null);
  const [qrExpiry, setQrExpiry] = useState(null);
  const [isConnecting, setIsConnecting] = useState(false);
  const [showSettings, setShowSettings] = useState(false);
  const [messages, setMessages] = useState([]);
  const [newMessage, setNewMessage] = useState('');
  const [connectionEstablished, setConnectionEstablished] = useState(false);
  const [error, setError] = useState(null);
  
  const qrTimerRef = useRef(null);
  const connectionCheckRef = useRef(null);

  // Auto-discover devices
  useEffect(() => {
    const discoverInterval = setInterval(() => {
      if (connectionState === 'disconnected') {
        // Simulate device discovery
        setAvailableNodes(prev => prev.map(node => ({
          ...node,
          signal: Math.max(20, Math.min(100, node.signal + (Math.random() - 0.5) * 20))
        })));
        setBluetoothDevices(prev => prev.map(device => ({
          ...device,
          signal: Math.max(20, Math.min(100, device.signal + (Math.random() - 0.5) * 15))
        })));
        setWifiNetworks(prev => prev.map(network => ({
          ...network,
          signal: Math.max(30, Math.min(100, network.signal + (Math.random() - 0.5) * 10))
        })));
      }
    }, 2000);

    return () => clearInterval(discoverInterval);
  }, [connectionState]);

  // QR code expiry timer
  useEffect(() => {
    if (qrExpiry) {
      qrTimerRef.current = setInterval(() => {
        const now = Date.now();
        if (now >= qrExpiry) {
          setQrCode(null);
          setQrExpiry(null);
          setError('QR code expired. Please generate a new one.');
        }
      }, 1000);
    }

    return () => {
      if (qrTimerRef.current) {
        clearInterval(qrTimerRef.current);
      }
    };
  }, [qrExpiry]);

  // Connection establishment check
  useEffect(() => {
    if (currentScreen === 'checking-connection') {
      connectionCheckRef.current = setTimeout(() => {
        // Simulate connection check
        const success = Math.random() > 0.3; // 70% success rate
        if (success) {
          setConnectionEstablished(true);
          setCurrentScreen('chat');
        } else {
          setError('Failed to establish secure connection. Please try again.');
          setCurrentScreen('qr-generation');
        }
      }, 3000);
    }

    return () => {
      if (connectionCheckRef.current) {
        clearTimeout(connectionCheckRef.current);
      }
    };
  }, [currentScreen]);

  const handleConnectionTypeSelect = (type) => {
    setConnectionType(type);
    setCurrentScreen('device-list');
  };

  const handleDeviceSelect = (device) => {
    if (connectionType === 'bluetooth') {
      setSelectedNode({
        id: device.id,
        name: device.name,
        signal: device.signal,
        type: 'bluetooth'
      });
    } else {
      setSelectedNode({
        id: device.id,
        name: device.name,
        signal: device.signal,
        type: 'wifi'
      });
    }
    setCurrentScreen('phone-settings');
  };

  const handleConnect = async () => {
    if (!selectedNode) {
      setError('Please select a device first');
      return;
    }

    setIsConnecting(true);
    setError(null);

    // Simulate connection process
    setTimeout(() => {
      const success = Math.random() > 0.2; // 80% success rate
      if (success) {
        setConnectionState('connected');
        setConnectedDevice(selectedNode);
        setCurrentScreen('continue');
        
        // Generate key pair
        const keys = generateKeyPair();
        setKeyPair(keys);
      } else {
        setError(`Failed to connect to ${selectedNode.name}. Please try again.`);
        setConnectionState('disconnected');
      }
      setIsConnecting(false);
    }, 2000);
  };

  const handleContinue = () => {
    setCurrentScreen('qr-generation');
    // Generate QR code
    const qrData = generateQRData(keyPair.publicKey, connectedDevice.id);
    setQrCode(qrData);
    setQrExpiry(Date.now() + 10 * 60 * 1000); // 10 minutes
  };

  const handleQRContinue = () => {
    setCurrentScreen('checking-connection');
  };

  const sendMessage = () => {
    if (!newMessage.trim()) return;

    const message = {
      id: Date.now(),
      text: newMessage,
      sender: 'you',
      timestamp: new Date().toLocaleTimeString(),
      encrypted: true
    };

    setMessages(prev => [...prev, message]);
    setNewMessage('');

    // Simulate response
    setTimeout(() => {
      const response = {
        id: Date.now() + 1,
        text: `Echo: ${newMessage}`,
        sender: 'peer',
        timestamp: new Date().toLocaleTimeString(),
        encrypted: true
      };
      setMessages(prev => [...prev, response]);
    }, 1000);
  };

  const resetApp = () => {
    setCurrentScreen('start');
    setConnectionState('disconnected');
    setConnectionType(null);
    setSelectedNode(null);
    setConnectedDevice(null);
    setKeyPair(null);
    setQrCode(null);
    setQrExpiry(null);
    setMessages([]);
    setConnectionEstablished(false);
    setError(null);
  };

  const formatTime = (ms) => {
    const minutes = Math.floor(ms / 60000);
    const seconds = Math.floor((ms % 60000) / 1000);
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  };

  // Start Screen
  if (currentScreen === 'start') {
    return (
      <div className="min-h-screen bg-gradient-to-br from-indigo-900 via-purple-900 to-black text-white p-4 flex items-center justify-center">
        <div className="text-center max-w-md mx-auto">
          <div className="w-24 h-24 mx-auto mb-8 rounded-full bg-gradient-to-r from-cyan-400 to-purple-500 flex items-center justify-center">
            <Router className="w-12 h-12" />
          </div>
          <h1 className="text-3xl font-bold mb-4">Fusion Node App</h1>
          <p className="text-purple-200 mb-8">Secure device-to-device communication</p>
          
          <button
            onClick={() => setCurrentScreen('connection-type')}
            className="w-full py-4 px-8 rounded-xl bg-gradient-to-r from-cyan-500 to-purple-600 hover:from-cyan-600 hover:to-purple-700 font-semibold text-lg shadow-lg transform hover:scale-105 transition-all"
          >
            <Smartphone className="w-6 h-6 inline mr-3" />
            Discover Devices
          </button>
        </div>
      </div>
    );
  }

  // Connection Type Selection Screen
  if (currentScreen === 'connection-type') {
    return (
      <div className="min-h-screen bg-gradient-to-br from-blue-900 via-indigo-900 to-black text-white p-4">
        <div className="max-w-md mx-auto pt-8">
          <div className="text-center mb-8">
            <h1 className="text-2xl font-bold mb-2">Select Connection Type</h1>
            <p className="text-blue-200">Choose how you want to connect to devices</p>
          </div>

          <div className="space-y-6">
            <button
              onClick={() => handleConnectionTypeSelect('bluetooth')}
              className="w-full p-6 rounded-xl bg-gradient-to-r from-blue-600 to-cyan-600 hover:from-blue-700 hover:to-cyan-700 text-left transition-all transform hover:scale-105"
            >
              <div className="flex items-center space-x-4">
                <div className="w-12 h-12 rounded-full bg-white/20 flex items-center justify-center">
                  <Wifi className="w-6 h-6" />
                </div>
                <div>
                  <h3 className="text-xl font-semibold">Bluetooth</h3>
                  <p className="text-blue-100 text-sm">Connect via Bluetooth Low Energy</p>
                  <p className="text-xs text-blue-200 mt-1">Range: ~100m • Power efficient</p>
                </div>
              </div>
            </button>

            <button
              onClick={() => handleConnectionTypeSelect('wifi')}
              className="w-full p-6 rounded-xl bg-gradient-to-r from-purple-600 to-pink-600 hover:from-purple-700 hover:to-pink-700 text-left transition-all transform hover:scale-105"
            >
              <div className="flex items-center space-x-4">
                <div className="w-12 h-12 rounded-full bg-white/20 flex items-center justify-center">
                  <Router className="w-6 h-6" />
                </div>
                <div>
                  <h3 className="text-xl font-semibold">Wi-Fi</h3>
                  <p className="text-pink-100 text-sm">Connect via Wi-Fi networks</p>
                  <p className="text-xs text-pink-200 mt-1">Range: ~300m • High bandwidth</p>
                </div>
              </div>
            </button>
          </div>

          <button
            onClick={() => setCurrentScreen('start')}
            className="w-full mt-8 py-3 px-6 rounded-xl border border-gray-600 hover:bg-gray-800/50 font-semibold"
          >
            Back
          </button>
        </div>
      </div>
    );
  }

  // Device List Screen
  if (currentScreen === 'device-list') {
    const devices = connectionType === 'bluetooth' ? bluetoothDevices : wifiNetworks;
    const title = connectionType === 'bluetooth' ? 'Bluetooth Devices' : 'Wi-Fi Networks';
    const IconComponent = connectionType === 'bluetooth' ? Wifi : Router;

    return (
      <div className="min-h-screen bg-gradient-to-br from-gray-900 via-blue-900 to-black text-white p-4">
        <div className="max-w-md mx-auto">
          <div className="text-center mb-8 pt-8">
            <div className="w-16 h-16 mx-auto mb-4 rounded-full bg-gradient-to-r from-cyan-400 to-purple-500 flex items-center justify-center">
              <IconComponent className="w-8 h-8" />
            </div>
            <h1 className="text-2xl font-bold mb-2">{title}</h1>
            <p className="text-blue-200">Scanning for available {connectionType} devices...</p>
          </div>

          <div className="space-y-4">
            {devices.map(device => (
              <div 
                key={device.id}
                className="p-4 rounded-xl border border-gray-600 bg-gray-800/50 hover:border-gray-500 cursor-pointer transition-all"
                onClick={() => handleDeviceSelect(device)}
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center space-x-3">
                    <div className="relative">
                      {connectionType === 'bluetooth' ? (
                        <Wifi className="w-6 h-6 text-blue-400" />
                      ) : (
                        <Router className="w-6 h-6 text-purple-400" />
                      )}
                      {device.paired && (
                        <div className="w-3 h-3 bg-green-400 rounded-full absolute -top-1 -right-1"></div>
                      )}
                      {device.secured && (
                        <div className="w-3 h-3 bg-yellow-400 rounded-full absolute -top-1 -right-1"></div>
                      )}
                    </div>
                    <div>
                      <h3 className="font-semibold">{device.name}</h3>
                      <p className="text-sm text-gray-400">
                        {connectionType === 'bluetooth' ? 
                          (device.paired ? 'Paired' : 'Available') : 
                          (device.secured ? 'Secured' : 'Open')
                        }
                      </p>
                    </div>
                  </div>
                  <div className="text-right">
                    <div className="text-sm font-semibold text-green-400">{device.signal}%</div>
                    <div className="text-xs text-gray-400">Signal</div>
                  </div>
                </div>
              </div>
            ))}
          </div>

          <button
            onClick={() => setCurrentScreen('connection-type')}
            className="w-full mt-8 py-3 px-6 rounded-xl border border-gray-600 hover:bg-gray-800/50 font-semibold"
          >
            Back to Connection Types
          </button>
        </div>
      </div>
    );
  }

  // Phone Settings Screen
  if (currentScreen === 'phone-settings') {
    return (
      <div className="min-h-screen bg-gradient-to-br from-green-900 via-teal-900 to-black text-white p-4">
        <div className="max-w-md mx-auto pt-8">
          <div className="text-center mb-8">
            <Settings className="w-16 h-16 mx-auto mb-4 text-green-400" />
            <h1 className="text-2xl font-bold mb-2">Phone Settings</h1>
            <p className="text-green-200">Configure connection settings</p>
          </div>

          <div className="space-y-6">
            <div className="bg-gray-800/50 p-4 rounded-xl">
              <h3 className="font-semibold text-green-400 mb-3">Selected Device</h3>
              <div className="flex items-center space-x-3">
                <div className="w-10 h-10 rounded-full bg-green-500/20 flex items-center justify-center">
                  {connectionType === 'bluetooth' ? 
                    <Wifi className="w-5 h-5 text-blue-400" /> : 
                    <Router className="w-5 h-5 text-purple-400" />
                  }
                </div>
                <div>
                  <p className="font-semibold">{selectedNode?.name}</p>
                  <p className="text-sm text-gray-400">
                    {connectionType === 'bluetooth' ? 'Bluetooth' : 'Wi-Fi'} • {selectedNode?.signal}% signal
                  </p>
                </div>
              </div>
            </div>

            <div className="bg-gray-800/50 p-4 rounded-xl">
              <h3 className="font-semibold text-green-400 mb-3">Connection Settings</h3>
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <span>Auto-connect</span>
                  <div className="w-12 h-6 bg-green-500 rounded-full flex items-center justify-end px-1">
                    <div className="w-4 h-4 bg-white rounded-full"></div>
                  </div>
                </div>
                <div className="flex items-center justify-between">
                  <span>Encryption</span>
                  <div className="w-12 h-6 bg-green-500 rounded-full flex items-center justify-end px-1">
                    <div className="w-4 h-4 bg-white rounded-full"></div>
                  </div>
                </div>
                <div className="flex items-center justify-between">
                  <span>Background sync</span>
                  <div className="w-12 h-6 bg-gray-600 rounded-full flex items-center justify-start px-1">
                    <div className="w-4 h-4 bg-white rounded-full"></div>
                  </div>
                </div>
              </div>
            </div>

            {error && (
              <div className="p-3 bg-red-500/20 border border-red-500/30 rounded-lg text-red-200 text-sm">
                {error}
              </div>
            )}

            <button
              onClick={handleConnect}
              disabled={isConnecting}
              className="w-full py-3 px-6 rounded-xl bg-gradient-to-r from-green-500 to-teal-600 hover:from-green-600 hover:to-teal-700 font-semibold disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {isConnecting ? (
                <div className="flex items-center justify-center">
                  <RefreshCw className="w-5 h-5 animate-spin mr-2" />
                  Connecting...
                </div>
              ) : (
                'Connect to Device'
              )}
            </button>

            <button
              onClick={() => setCurrentScreen('device-list')}
              className="w-full py-3 px-6 rounded-xl border border-gray-600 hover:bg-gray-800/50 font-semibold"
            >
              Back to Device List
            </button>
          </div>
        </div>
      </div>
    );
  }

  // Continue Screen
  if (currentScreen === 'continue') {
    return (
      <div className="min-h-screen bg-gradient-to-br from-green-900 via-blue-900 to-black text-white p-4 flex items-center justify-center">
        <div className="text-center max-w-md mx-auto">
          <div className="w-20 h-20 mx-auto mb-6 rounded-full bg-gradient-to-r from-green-400 to-blue-500 flex items-center justify-center">
            <Check className="w-10 h-10" />
          </div>
          <h1 className="text-2xl font-bold mb-4">Connection Established!</h1>
          <div className="p-4 bg-gray-800/50 rounded-xl mb-6">
            <p className="text-green-400 font-semibold">{connectedDevice?.name}</p>
            <p className="text-sm text-gray-300 mt-1">Device ID: {connectedDevice?.id}</p>
            <div className="flex items-center justify-center mt-3 space-x-2">
              <Key className="w-4 h-4 text-blue-400" />
              <span className="text-xs text-blue-300">Keys Generated</span>
            </div>
          </div>
          <button
            onClick={handleContinue}
            className="w-full py-3 px-6 rounded-xl bg-gradient-to-r from-green-500 to-blue-600 hover:from-green-600 hover:to-blue-700 font-semibold shadow-lg"
          >
            Continue to QR Generation
          </button>
        </div>
      </div>
    );
  }

  // QR Generation Screen
  if (currentScreen === 'qr-generation') {
    const timeLeft = qrExpiry ? qrExpiry - Date.now() : 0;
    
    return (
      <div className="min-h-screen bg-gradient-to-br from-purple-900 via-blue-900 to-black text-white p-4">
        <div className="max-w-md mx-auto pt-8">
          <div className="text-center mb-6">
            <QrCode className="w-12 h-12 mx-auto mb-3 text-purple-400" />
            <h1 className="text-2xl font-bold mb-2">Secure QR Code</h1>
            <p className="text-purple-200">Share this QR code with the other device</p>
          </div>

          <div className="bg-white p-6 rounded-xl mb-6">
            <div className="w-48 h-48 mx-auto bg-black flex items-center justify-center text-white text-xs text-center">
              QR CODE<br/>
              {qrCode?.substring(0, 20)}...<br/>
              <QrCode className="w-8 h-8 mx-auto mt-2" />
            </div>
          </div>

          <div className="bg-gray-800/50 p-4 rounded-xl mb-6">
            <div className="flex items-center justify-between">
              <div className="flex items-center space-x-2">
                <Timer className="w-5 h-5 text-orange-400" />
                <span className="font-semibold">Time Remaining:</span>
              </div>
              <span className="text-orange-400 font-mono text-lg">
                {timeLeft > 0 ? formatTime(timeLeft) : '00:00'}
              </span>
            </div>
          </div>

          {error && (
            <div className="mb-4 p-3 bg-red-500/20 border border-red-500/30 rounded-lg text-red-200 text-sm">
              {error}
            </div>
          )}

          <button
            onClick={handleQRContinue}
            disabled={timeLeft <= 0}
            className={`w-full py-3 px-6 rounded-xl font-semibold ${
              timeLeft > 0
                ? 'bg-gradient-to-r from-purple-500 to-blue-600 hover:from-purple-600 hover:to-blue-700'
                : 'bg-gray-600 cursor-not-allowed'
            }`}
          >
            Continue
          </button>
        </div>
      </div>
    );
  }

  // Connection Check Screen
  if (currentScreen === 'checking-connection') {
    return (
      <div className="min-h-screen bg-gradient-to-br from-yellow-900 via-orange-900 to-black text-white p-4 flex items-center justify-center">
        <div className="text-center max-w-md mx-auto">
          <div className="w-20 h-20 mx-auto mb-6 rounded-full bg-gradient-to-r from-yellow-400 to-orange-500 flex items-center justify-center">
            <RefreshCw className="w-10 h-10 animate-spin" />
          </div>
          <h1 className="text-2xl font-bold mb-4">Establishing Connection</h1>
          <p className="text-yellow-200 mb-6">Verifying secure connection between devices...</p>
          <div className="bg-gray-800/50 p-4 rounded-xl">
            <div className="flex items-center justify-center space-x-2">
              <Smartphone className="w-5 h-5" />
              <div className="text-yellow-400">···</div>
              <Router className="w-5 h-5" />
              <div className="text-yellow-400">···</div>
              <Smartphone className="w-5 h-5" />
            </div>
            <p className="text-sm text-gray-300 mt-2">Phone A ↔ Fusion Node ↔ Relay ↔ Phone B</p>
          </div>
        </div>
      </div>
    );
  }

  // Chat Screen
  if (currentScreen === 'chat') {
    return (
      <div className="min-h-screen bg-gradient-to-br from-gray-900 to-black text-white flex flex-col">
        {/* Header */}
        <div className="bg-gray-800/50 p-4 border-b border-gray-700">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-3">
              <div className="w-10 h-10 rounded-full bg-green-500 flex items-center justify-center">
                <MessageCircle className="w-5 h-5" />
              </div>
              <div>
                <h2 className="font-semibold">Secure Chat</h2>
                <p className="text-xs text-green-400 flex items-center">
                  <div className="w-2 h-2 bg-green-400 rounded-full mr-1"></div>
                  Connected via {connectedDevice?.name}
                </p>
              </div>
            </div>
            <button
              onClick={resetApp}
              className="p-2 rounded-lg hover:bg-gray-700"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Messages */}
        <div className="flex-1 p-4 overflow-y-auto">
          <div className="space-y-4">
            {messages.length === 0 ? (
              <div className="text-center text-gray-400 py-8">
                <MessageCircle className="w-12 h-12 mx-auto mb-3 opacity-50" />
                <p>Start your secure conversation</p>
              </div>
            ) : (
              messages.map(message => (
                <div
                  key={message.id}
                  className={`flex ${message.sender === 'you' ? 'justify-end' : 'justify-start'}`}
                >
                  <div
                    className={`max-w-xs lg:max-w-md px-4 py-2 rounded-xl ${
                      message.sender === 'you'
                        ? 'bg-blue-600 text-white'
                        : 'bg-gray-700 text-white'
                    }`}
                  >
                    <p className="text-sm">{message.text}</p>
                    <p className="text-xs opacity-70 mt-1 flex items-center">
                      {message.encrypted && <Key className="w-3 h-3 mr-1" />}
                      {message.timestamp}
                    </p>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Message Input */}
        <div className="p-4 border-t border-gray-700">
          <div className="flex items-center space-x-3">
            <input
              type="text"
              value={newMessage}
              onChange={(e) => setNewMessage(e.target.value)}
              onKeyPress={(e) => e.key === 'Enter' && sendMessage()}
              placeholder="Type your encrypted message..."
              className="flex-1 bg-gray-800 border border-gray-600 rounded-xl px-4 py-2 focus:outline-none focus:border-blue-500"
            />
            <button
              onClick={sendMessage}
              disabled={!newMessage.trim()}
              className="w-10 h-10 rounded-xl bg-blue-600 hover:bg-blue-700 disabled:bg-gray-600 disabled:cursor-not-allowed flex items-center justify-center"
            >
              <Send className="w-5 h-5" />
            </button>
          </div>
          <p className="text-xs text-gray-400 mt-2 text-center">
            End-to-end encrypted via fusion node relay
          </p>
        </div>
      </div>
    );
  }

  return null;
};

export default FusionNodeApp;











