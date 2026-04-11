import test from 'node:test';
import assert from 'node:assert/strict';
import { SessionRegistry } from '../src/sessionRegistry.js';

test('同一 sessionId 下重复加入相同角色会被拒绝', () => {
    const registry = new SessionRegistry();
    const senderA = { id: 'sender-a' };
    const senderB = { id: 'sender-b' };

    registry.join({ sessionId: 'room-1', role: 'sender', peer: senderA });

    assert.throws(
        () => {
            registry.join({ sessionId: 'room-1', role: 'sender', peer: senderB });
        },
        /sender 已经加入当前会话/,
    );
});

test('receiver 后加入时会收到缓存的 offer 与 sender ICE', () => {
    const registry = new SessionRegistry();
    const sender = { id: 'sender' };
    const receiver = { id: 'receiver' };

    registry.join({ sessionId: 'room-2', role: 'sender', peer: sender });
    registry.relay({
        sessionId: 'room-2',
        role: 'sender',
        message: { type: 'offer', sessionId: 'room-2', sdp: 'offer-sdp' },
    });
    registry.relay({
        sessionId: 'room-2',
        role: 'sender',
        message: {
            type: 'ice-candidate',
            sessionId: 'room-2',
            candidate: 'candidate-1',
            sdpMid: '0',
            sdpMLineIndex: 0,
        },
    });

    const result = registry.join({ sessionId: 'room-2', role: 'receiver', peer: receiver });

    assert.deepEqual(result.deliveries, [
        {
            peer: receiver,
            message: { type: 'offer', sessionId: 'room-2', sdp: 'offer-sdp' },
        },
        {
            peer: receiver,
            message: {
                type: 'ice-candidate',
                sessionId: 'room-2',
                candidate: 'candidate-1',
                sdpMid: '0',
                sdpMLineIndex: 0,
            },
        },
    ]);
});

test('任一角色离开时会通知对端并清理会话', () => {
    const registry = new SessionRegistry();
    const sender = { id: 'sender' };
    const receiver = { id: 'receiver' };

    registry.join({ sessionId: 'room-3', role: 'sender', peer: sender });
    registry.join({ sessionId: 'room-3', role: 'receiver', peer: receiver });

    const result = registry.leave({ sessionId: 'room-3', role: 'sender' });

    assert.deepEqual(result.deliveries, [
        {
            peer: receiver,
            message: {
                type: 'leave',
                sessionId: 'room-3',
            },
        },
    ]);
    assert.deepEqual(result.unboundPeers, [sender, receiver]);
    assert.equal(registry.hasSession('room-3'), false);
});
