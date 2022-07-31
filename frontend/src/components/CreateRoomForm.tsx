import { instanceToPlain } from "class-transformer";
import { ChangeEvent, useState } from "react";
import { SendJsonMessage } from "react-use-websocket/dist/lib/types";
import CreateGameInput from "../domain/LobbyMessages/CreateGameInput";
import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';

export default function CreateRoomForm({ sendJsonMessage }: { sendJsonMessage: SendJsonMessage }) {

      const [roomNameInput, setRoomNameInput] = useState<string>("");


      const onSubmit = (e: React.FormEvent) => {
            e.preventDefault();

            let createRoomCommand = new CreateGameInput(roomNameInput.trim())
            sendJsonMessage(instanceToPlain(createRoomCommand))
      }
      return (
            <Row>
                  <Form onSubmit={onSubmit}>
                        <Row>
                              <Col xs={7}>
                                    <Form.Control placeholder="Game name" onChange={(e: ChangeEvent<HTMLInputElement>) => setRoomNameInput(e.currentTarget.value)} />
                              </Col>
                              <Col xs="auto">
                                    <Button type="submit" className="mb-2">Create</Button>
                              </Col>
                        </Row>
                  </Form>
            </Row>
      )
}