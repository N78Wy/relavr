const VALID_ROLES = new Set(['sender', 'receiver']);
const FORWARDABLE_TYPES = new Set(['offer', 'answer', 'ice-candidate', 'leave', 'error']);

export class SessionRegistry {
    #sessions = new Map();

    join({ sessionId, role, peer }) {
        this.#assertRole(role);

        const session = this.#getOrCreateSession(sessionId);
        if (session[role] !== null) {
            throw new Error(`${role} 已经加入当前会话`);
        }

        session[role] = peer;

        const deliveries = [];
        if (role === 'receiver' && session.cachedOffer !== null) {
            deliveries.push({
                peer,
                message: session.cachedOffer,
            });
            for (const candidate of session.bufferedSenderCandidates) {
                deliveries.push({
                    peer,
                    message: candidate,
                });
            }
        }

        return {
            deliveries,
            unboundPeers: [],
        };
    }

    relay({ sessionId, role, message }) {
        this.#assertRole(role);
        if (!FORWARDABLE_TYPES.has(message.type)) {
            throw new Error(`未知的信令消息类型: ${message.type}`);
        }

        if (message.type === 'leave') {
            return this.leave({ sessionId, role });
        }

        const session = this.#requireSession(sessionId);
        const targetRole = oppositeRole(role);
        const targetPeer = session[targetRole];
        const deliveries = [];

        switch (message.type) {
            case 'offer':
                if (role !== 'sender') {
                    throw new Error('只有 sender 可以发送 offer');
                }
                session.cachedOffer = message;
                session.bufferedSenderCandidates = [];
                if (targetPeer !== null) {
                    deliveries.push({ peer: targetPeer, message });
                }
                break;
            case 'answer':
                if (role !== 'receiver') {
                    throw new Error('只有 receiver 可以发送 answer');
                }
                if (targetPeer !== null) {
                    deliveries.push({ peer: targetPeer, message });
                }
                break;
            case 'ice-candidate':
                if (role === 'sender') {
                    if (targetPeer !== null) {
                        deliveries.push({ peer: targetPeer, message });
                    } else {
                        session.bufferedSenderCandidates.push(message);
                    }
                } else if (targetPeer !== null) {
                    deliveries.push({ peer: targetPeer, message });
                }
                break;
            case 'error':
                if (targetPeer !== null) {
                    deliveries.push({ peer: targetPeer, message });
                }
                break;
            default:
                throw new Error(`不支持的信令消息类型: ${message.type}`);
        }

        return {
            deliveries,
            unboundPeers: [],
        };
    }

    leave({ sessionId, role }) {
        this.#assertRole(role);

        const session = this.#sessions.get(sessionId);
        if (!session) {
            return {
                deliveries: [],
                unboundPeers: [],
            };
        }

        const counterpart = session[oppositeRole(role)];
        const unboundPeers = [session.sender, session.receiver].filter((peer) => peer !== null);
        this.#sessions.delete(sessionId);

        return {
            deliveries:
                counterpart === null
                    ? []
                    : [
                          {
                              peer: counterpart,
                              message: {
                                  type: 'leave',
                                  sessionId,
                              },
                          },
                      ],
            unboundPeers,
        };
    }

    hasSession(sessionId) {
        return this.#sessions.has(sessionId);
    }

    #getOrCreateSession(sessionId) {
        const existing = this.#sessions.get(sessionId);
        if (existing) {
            return existing;
        }

        const created = {
            sender: null,
            receiver: null,
            cachedOffer: null,
            bufferedSenderCandidates: [],
        };
        this.#sessions.set(sessionId, created);
        return created;
    }

    #requireSession(sessionId) {
        const session = this.#sessions.get(sessionId);
        if (!session) {
            throw new Error('会话不存在，请先发送 join');
        }
        return session;
    }

    #assertRole(role) {
        if (!VALID_ROLES.has(role)) {
            throw new Error(`无效角色: ${role}`);
        }
    }
}

function oppositeRole(role) {
    return role === 'sender' ? 'receiver' : 'sender';
}
