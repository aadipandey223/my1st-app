import React, { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';

const SecureChat = () => {
  const [currentStep, setCurrentStep] = useState('welcome');
  const [keyPair, setKeyPair] = useState(null);
  const [receiverPublicKey, setReceiverPublicKey] = useState('');
  const [sessionKeys, setSessionKeys] = useState(null);
  const [messages, setMessages] = useState([]);
  const [messageInput, setMessageInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [progress, setProgress] = useState(0);
  const messagesEndRef = useRef(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  // Generate mock cryptographic keys
  const generateKeys = async () => {
    setLoading(true);
    setProgress(0);

    // Simulate key generation with progress
    for (let i = 0; i <= 100; i += 10) {
      setProgress(i);
      await new Promise(resolve => setTimeout(resolve, 100));
    }

    // Generate mock X25519 keypair
    const mockKeyPair = {
      publicKey: Array.from({length: 32}, () => Math.floor(Math.random() * 256))
        .map(x => x.toString(16).padStart(2, '0')).join('').toUpperCase(),
      privateKey: Array.from({length: 32}, () => Math.floor(Math.random() * 256))
        .map(x => x.toString(16).padStart(2, '0')).join('').toUpperCase()
    };

    setKeyPair(mockKeyPair);
    setLoading(false);
    setTimeout(() => setCurrentStep('displayKey'), 500);
  };

  const handleReceiverKey = () => {
    setCurrentStep('enterReceiverKey');
  };

  const performKeyExchange = async () => {
    if (!receiverPublicKey.trim()) {
      alert('Please enter receiver\'s public key');
      return;
    }

    setCurrentStep('keyExchange');
    setLoading(true);
    setProgress(0);

    // Simulate ECDH key exchange
    const stages = [
      { progress: 25, message: 'Validating receiver key...' },
      { progress: 50, message: 'Computing shared secret...' },
      { progress: 75, message: 'Deriving session keys...' },
      { progress: 100, message: 'Key exchange complete!' }
    ];

    for (const stage of stages) {
      setProgress(stage.progress);
      await new Promise(resolve => setTimeout(resolve, 800));
    }

    setLoading(false);
    setTimeout(() => setCurrentStep('sessionKey'), 500);
  };

  const generateSessionKey = async () => {
    setLoading(true);
    setProgress(0);

    // Simulate session key generation
    for (let i = 0; i <= 100; i += 20) {
      setProgress(i);
      await new Promise(resolve => setTimeout(resolve, 200));
    }

    const mockSessionKeys = {
      sendKey: Array.from({length: 32}, () => Math.floor(Math.random() * 256)),
      receiveKey: Array.from({length: 32}, () => Math.floor(Math.random() * 256))
    };

    setSessionKeys(mockSessionKeys);
    setLoading(false);
    setTimeout(() => setCurrentStep('chat'), 1000);
  };

  const sendMessage = () => {
    if (!messageInput.trim()) return;

    const newMessage = {
      id: Date.now(),
      text: messageInput,
      type: 'sent',
      timestamp: new Date().toLocaleTimeString()
    };

    setMessages(prev => [...prev, newMessage]);
    setMessageInput('');

    // Simulate encrypted response
    setTimeout(() => {
      const response = {
        id: Date.now() + 1,
        text: '🔒 Message received and decrypted successfully!',
        type: 'received',
        timestamp: new Date().toLocaleTimeString()
      };
      setMessages(prev => [...prev, response]);
    }, 1000);
  };

  const handleKeyPress = (e) => {
    if (e.key === 'Enter') {
      sendMessage();
    }
  };

  // Animation variants
  const containerVariants = {
    hidden: { opacity: 0, y: 50 },
    visible: {
      opacity: 1,
      y: 0,
      transition: {
        duration: 0.6,
        ease: [0.4, 0, 0.2, 1]
      }
    },
    exit: {
      opacity: 0,
      y: -50,
      transition: {
        duration: 0.3
      }
    }
  };

  const buttonVariants = {
    hover: {
      scale: 1.02,
      y: -2,
      transition: { duration: 0.2 }
    },
    tap: { scale: 0.98 }
  };

  // Background orbs component
  const FloatingOrbs = () => (
    <div className="fixed inset-0 pointer-events-none overflow-hidden">
      {[...Array(6)].map((_, i) => (
        <motion.div
          key={i}
          className="absolute rounded-full opacity-20"
          style={{
            background: `linear-gradient(45deg, #6366f1, #8b5cf6)`,
            width: Math.random() * 100 + 50,
            height: Math.random() * 100 + 50,
            left: Math.random() * 100 + '%',
            top: Math.random() * 100 + '%',
          }}
          animate={{
            x: [0, Math.random() * 200 - 100],
            y: [0, Math.random() * 200 - 100],
            scale: [1, 1.2, 1],
          }}
          transition={{
            duration: Math.random() * 10 + 10,
            repeat: Infinity,
            ease: 'easeInOut',
          }}
        />
      ))}
    </div>
  );

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-purple-900 to-slate-900">
      <FloatingOrbs />

      <div className="relative z-10 flex items-center justify-center min-h-screen p-4">
        <AnimatePresence mode="wait">
          {currentStep === 'welcome' && (
            <motion.div
              key="welcome"
              variants={containerVariants}
              initial="hidden"
              animate="visible"
              exit="exit"
              className="w-full max-w-md"
            >
              <div className="bg-white/10 backdrop-blur-xl rounded-3xl p-8 shadow-2xl border border-white/20">
                <div className="text-center mb-8">
                  <motion.div
                    className="w-20 h-20 mx-auto mb-6 bg-gradient-to-r from-violet-500 to-purple-600 rounded-2xl flex items-center justify-center text-4xl"
                    animate={{
                      rotate: [0, 5, -5, 0],
                      scale: [1, 1.05, 1]
                    }}
                    transition={{
                      duration: 3,
                      repeat: Infinity,
                      ease: "easeInOut"
                    }}
                  >
                    🔐
                  </motion.div>
                  <h1 className="text-3xl font-bold bg-gradient-to-r from-violet-400 to-purple-400 bg-clip-text text-transparent mb-2">
                    SecureChat
                  </h1>
                  <p className="text-gray-300">End-to-End Encrypted Messaging</p>
                </div>

                <motion.button
                  variants={buttonVariants}
                  whileHover="hover"
                  whileTap="tap"
                  onClick={generateKeys}
                  disabled={loading}
                  className="w-full bg-gradient-to-r from-violet-600 to-purple-600 hover:from-violet-700 hover:to-purple-700 text-white font-semibold py-4 px-6 rounded-2xl shadow-lg transition-all duration-300 disabled:opacity-50 flex items-center justify-center gap-3"
                >
                  {loading ? (
                    <>
                      <motion.div
                        className="w-5 h-5 border-2 border-white border-t-transparent rounded-full"
                        animate={{ rotate: 360 }}
                        transition={{ duration: 1, repeat: Infinity, ease: 'linear' }}
                      />
                      Generating Keys... {progress}%
                    </>
                  ) : (
                    <>
                      🔑 Generate Encryption Keys
                    </>
                  )}
                </motion.button>

                <div className="mt-6 p-4 bg-blue-500/10 border border-blue-500/20 rounded-xl">
                  <p className="text-sm text-blue-300 flex items-center gap-2">
                    🛡️ Keys are generated locally and stored securely
                  </p>
                </div>
              </div>
            </motion.div>
          )}

          {currentStep === 'displayKey' && keyPair && (
            <motion.div
              key="displayKey"
              variants={containerVariants}
              initial="hidden"
              animate="visible"
              exit="exit"
              className="w-full max-w-md"
            >
              <div className="bg-white/10 backdrop-blur-xl rounded-3xl p-8 shadow-2xl border border-white/20">
                <div className="text-center mb-6">
                  <h2 className="text-2xl font-bold text-white mb-2">Your Public Key</h2>
                  <p className="text-gray-300">Share this with your contact</p>
                </div>

                <div className="bg-white/5 rounded-2xl p-6 mb-6 border border-white/10">
                  <div className="text-center mb-4">
                    <motion.div
                      className="w-32 h-32 mx-auto bg-white rounded-2xl flex items-center justify-center text-6xl mb-4 shadow-lg"
                      animate={{
                        boxShadow: ['0 0 0 rgba(139, 92, 246, 0)', '0 0 30px rgba(139, 92, 246, 0.3)', '0 0 0 rgba(139, 92, 246, 0)']
                      }}
                      transition={{
                        duration: 2,
                        repeat: Infinity
                      }}
                    >
                      📱
                    </motion.div>
                    <p className="text-xs text-gray-400 mb-3">Public Key (X25519)</p>
                    <div className="bg-gray-800 rounded-lg p-3 font-mono text-xs break-all text-gray-300 border">
                      {keyPair.publicKey}
                    </div>
                  </div>
                </div>

                <motion.button
                  variants={buttonVariants}
                  whileHover="hover"
                  whileTap="tap"
                  onClick={handleReceiverKey}
                  className="w-full bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-700 hover:to-teal-700 text-white font-semibold py-4 px-6 rounded-2xl shadow-lg transition-all duration-300"
                >
                  📥 Enter Receiver's Key
                </motion.button>
              </div>
            </motion.div>
          )}

          {currentStep === 'enterReceiverKey' && (
            <motion.div
              key="enterReceiverKey"
              variants={containerVariants}
              initial="hidden"
              animate="visible"
              exit="exit"
              className="w-full max-w-md"
            >
              <div className="bg-white/10 backdrop-blur-xl rounded-3xl p-8 shadow-2xl border border-white/20">
                <div className="text-center mb-6">
                  <h2 className="text-2xl font-bold text-white mb-2">Receiver's Key</h2>
                  <p className="text-gray-300">Enter your contact's public key</p>
                </div>

                <div className="mb-6">
                  <label className="block text-sm font-medium text-gray-300 mb-3">
                    Public Key
                  </label>
                  <textarea
                    value={receiverPublicKey}
                    onChange={(e) => setReceiverPublicKey(e.target.value)}
                    placeholder="Paste the receiver's public key here..."
                    className="w-full h-32 bg-white/5 border border-white/20 rounded-2xl px-4 py-3 text-white placeholder-gray-400 resize-none focus:outline-none focus:ring-2 focus:ring-violet-500 focus:border-transparent transition-all duration-300"
                  />
                </div>

                <div className="space-y-3">
                  <motion.button
                    variants={buttonVariants}
                    whileHover="hover"
                    whileTap="tap"
                    onClick={performKeyExchange}
                    className="w-full bg-gradient-to-r from-violet-600 to-purple-600 hover:from-violet-700 hover:to-purple-700 text-white font-semibold py-4 px-6 rounded-2xl shadow-lg transition-all duration-300"
                  >
                    🤝 Perform Key Exchange
                  </motion.button>

                  <motion.button
                    variants={buttonVariants}
                    whileHover="hover"
                    whileTap="tap"
                    onClick={() => setReceiverPublicKey('A1B2C3D4E5F6789012345678901234567890ABCDEF1234567890ABCDEF123456')}
                    className="w-full bg-white/10 hover:bg-white/20 text-white font-semibold py-4 px-6 rounded-2xl border border-white/20 transition-all duration-300"
                  >
                    📷 Scan QR Code (Demo)
                  </motion.button>
                </div>
              </div>
            </motion.div>
          )}

          {currentStep === 'keyExchange' && (
            <motion.div
              key="keyExchange"
              variants={containerVariants}
              initial="hidden"
              animate="visible"
              exit="exit"
              className="w-full max-w-md"
            >
              <div className="bg-white/10 backdrop-blur-xl rounded-3xl p-8 shadow-2xl border border-white/20">
                <div className="text-center mb-8">
                  <motion.div
                    className="w-16 h-16 mx-auto mb-6 bg-gradient-to-r from-blue-500 to-cyan-500 rounded-full flex items-center justify-center text-2xl"
                    animate={{ rotate: 360 }}
                    transition={{ duration: 2, repeat: Infinity, ease: 'linear' }}
                  >
                    🔄
                  </motion.div>
                  <h2 className="text-2xl font-bold text-white mb-2">Key Exchange</h2>
                  <p className="text-gray-300">Establishing secure connection...</p>
                </div>

                <div className="mb-6">
                  <div className="bg-gray-800 rounded-full h-2 overflow-hidden">
                    <motion.div
                      className="h-full bg-gradient-to-r from-blue-500 to-cyan-500"
                      initial={{ width: 0 }}
                      animate={{ width: `${progress}%` }}
                      transition={{ duration: 0.3 }}
                    />
                  </div>
                  <p className="text-center text-sm text-gray-400 mt-2">{progress}% Complete</p>
                </div>

                {progress === 100 && (
                  <motion.button
                    initial={{ opacity: 0, y: 20 }}
                    animate={{ opacity: 1, y: 0 }}
                    variants={buttonVariants}
                    whileHover="hover"
                    whileTap="tap"
                    onClick={generateSessionKey}
                    className="w-full bg-gradient-to-r from-violet-600 to-purple-600 hover:from-violet-700 hover:to-purple-700 text-white font-semibold py-4 px-6 rounded-2xl shadow-lg transition-all duration-300"
                  >
                    🔐 Generate Session Key
                  </motion.button>
                )}
              </div>
            </motion.div>
          )}

          {currentStep === 'sessionKey' && (
            <motion.div
              key="sessionKey"
              variants={containerVariants}
              initial="hidden"
              animate="visible"
              exit="exit"
              className="w-full max-w-md"
            >
              <div className="bg-white/10 backdrop-blur-xl rounded-3xl p-8 shadow-2xl border border-white/20">
                <div className="text-center mb-8">
                  <motion.div
                    className="w-16 h-16 mx-auto mb-6 bg-gradient-to-r from-green-500 to-emerald-500 rounded-full flex items-center justify-center text-2xl"
                    animate={{ scale: [1, 1.1, 1] }}
                    transition={{ duration: 1, repeat: Infinity }}
                  >
                    🔐
                  </motion.div>
                  <h2 className="text-2xl font-bold text-white mb-2">Session Key</h2>
                  <p className="text-gray-300">Generating secure session keys...</p>
                </div>

                <div className="mb-6">
                  <div className="bg-gray-800 rounded-full h-2 overflow-hidden">
                    <motion.div
                      className="h-full bg-gradient-to-r from-green-500 to-emerald-500"
                      initial={{ width: 0 }}
                      animate={{ width: `${progress}%` }}
                      transition={{ duration: 0.3 }}
                    />
                  </div>
                  <p className="text-center text-sm text-gray-400 mt-2">{progress}% Complete</p>
                </div>

                {progress === 100 && (
                  <motion.div
                    initial={{ opacity: 0, y: 20 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="space-y-4"
                  >
                    <div className="bg-green-500/10 border border-green-500/20 rounded-xl p-4">
                      <p className="text-green-400 text-sm flex items-center gap-2">
                        ✅ Session keys generated successfully!
                      </p>
                    </div>

                    <motion.button
                      variants={buttonVariants}
                      whileHover="hover"
                      whileTap="tap"
                      onClick={() => setCurrentStep('chat')}
                      className="w-full bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-700 hover:to-teal-700 text-white font-semibold py-4 px-6 rounded-2xl shadow-lg transition-all duration-300"
                    >
                      💬 Open Secure Chat
                    </motion.button>
                  </motion.div>
                )}
              </div>
            </motion.div>
          )}

          {currentStep === 'chat' && (
            <motion.div
              key="chat"
              variants={containerVariants}
              initial="hidden"
              animate="visible"
              exit="exit"
              className="w-full max-w-lg h-[600px]"
            >
              <div className="bg-white/10 backdrop-blur-xl rounded-3xl shadow-2xl border border-white/20 h-full flex flex-col overflow-hidden">
                {/* Chat Header */}
                <div className="bg-white/5 px-6 py-4 border-b border-white/10">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 bg-gradient-to-r from-violet-500 to-purple-600 rounded-full flex items-center justify-center text-sm font-bold">
                      SC
                    </div>
                    <div>
                      <h3 className="font-semibold text-white">Secure Contact</h3>
                      <p className="text-xs text-green-400 flex items-center gap-1">
                        🔒 End-to-end encrypted
                      </p>
                    </div>
                  </div>
                </div>

                {/* Messages */}
                <div className="flex-1 overflow-y-auto p-6 space-y-4">
                  <div className="bg-blue-500/10 border border-blue-500/20 rounded-2xl p-4 max-w-xs">
                    <p className="text-sm text-blue-300">
                      🔒 This conversation is end-to-end encrypted. Only you and your contact can read these messages.
                    </p>
                  </div>

                  {messages.map((message) => (
                    <motion.div
                      key={message.id}
                      initial={{ opacity: 0, y: 20 }}
                      animate={{ opacity: 1, y: 0 }}
                      className={`flex ${message.type === 'sent' ? 'justify-end' : 'justify-start'}`}
                    >
                      <div
                        className={`max-w-xs px-4 py-3 rounded-2xl ${
                          message.type === 'sent'
                            ? 'bg-gradient-to-r from-violet-600 to-purple-600 text-white rounded-br-sm'
                            : 'bg-white/10 text-white rounded-bl-sm border border-white/20'
                        }`}
                      >
                        <p className="text-sm">{message.text}</p>
                        <p className="text-xs opacity-70 mt-1">{message.timestamp}</p>
                      </div>
                    </motion.div>
                  ))}
                  <div ref={messagesEndRef} />
                </div>

                {/* Input */}
                <div className="bg-white/5 px-6 py-4 border-t border-white/10">
                  <div className="flex items-center gap-3">
                    <input
                      type="text"
                      value={messageInput}
                      onChange={(e) => setMessageInput(e.target.value)}
                      onKeyPress={handleKeyPress}
                      placeholder="Type your message..."
                      className="flex-1 bg-white/10 border border-white/20 rounded-full px-4 py-3 text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-violet-500 focus:border-transparent transition-all duration-300"
                    />
                    <motion.button
                      whileHover={{ scale: 1.05 }}
                      whileTap={{ scale: 0.95 }}
                      onClick={sendMessage}
                      className="w-12 h-12 bg-gradient-to-r from-violet-600 to-purple-600 hover:from-violet-700 hover:to-purple-700 rounded-full flex items-center justify-center text-white font-bold shadow-lg transition-all duration-300"
                    >
                      ➤
                    </motion.button>
                  </div>
                </div>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  );
};

export default SecureChat;