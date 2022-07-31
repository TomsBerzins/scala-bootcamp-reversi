import { instanceToPlain } from 'class-transformer';
import {
    ChangeEvent,
    useState
} from 'react';
import Col from 'react-bootstrap/Col';
import Row from 'react-bootstrap/Row';
import Form from 'react-bootstrap/Form';
import Button from 'react-bootstrap/Button';
import ChatMessage from '../domain/LobbyMessages/ChatOutput';
import ChatInput from '../domain/LobbyMessages/ChatInput';
import { SendJsonMessage } from 'react-use-websocket/dist/lib/types';
import GeneralMessage from '../domain/LobbyMessages/GeneralMessage';



export default function ChatBox({ messageHistory, sendJsonMessage }: { messageHistory: (ChatMessage | GeneralMessage)[], sendJsonMessage: SendJsonMessage }) {

    const [chatInput, setChatInput] = useState<string>("");

    function handleChatSubmit(e: React.SyntheticEvent) {
        e.preventDefault();
        sendJsonMessage(instanceToPlain(new ChatInput(chatInput)))
    }

    function handleChatInput(msg: string) {
        setChatInput(msg)
    }

    function renderElement(id: any,msg: (ChatMessage | GeneralMessage)) {
        if (msg instanceof ChatMessage) {
            return <li key={id} className="chat-message">{msg.sender.name} :{msg.message}</li>
        } else if (msg instanceof GeneralMessage) {
            return <li key={id} className="chat-server-message">{msg.message}</li>
        }
    }

    return (
        <Row className="h-100">
            <Col className="h-100">
                <Row style={{ maxHeight: '90%', height: '90% ' }} className="overflow-auto">
                    <Col >
                        <ul className="chat-container">
                            {messageHistory.map((message, idx) => (
                               renderElement(idx,message)
                            ))}
                        </ul>
                    </Col>
                </Row>
                <Row>
                    <Col>
                        <Form>
                            <Row className="p-2 align-items-center">
                                <Col xs={10}>
                                    <Form.Control
                                        id="inlineFormInput"
                                        placeholder="Message"
                                        onChange={(e: ChangeEvent<HTMLInputElement>) => handleChatInput(e.currentTarget.value)}
                                    />
                                </Col>
                                <Col xs={2}>
                                    <Button type="submit" onClick={handleChatSubmit} variant="secondary">
                                        Send
                                    </Button>
                                </Col>
                            </Row>
                        </Form>
                    </Col>
                </Row>
            </Col>
        </Row>
    )
}