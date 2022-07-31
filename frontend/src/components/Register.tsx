import { ChangeEvent, useContext, useState } from "react"
import { Col, Container, Row, Form, Button } from "react-bootstrap";
import { playerContext } from "../auth/PlayerProvider"
import Navigation from "./Navigation";
import { useNavigate } from "react-router-dom";

export default function Register() {

  let navigate = useNavigate();

  const { signIn } = useContext(playerContext)

  const [userName, setUserName] = useState<string>("");

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    signIn(userName, () => {
      navigate("/", { replace: true });
    });
    

  }
  return (
    <Container className="main-container">
      <Row className="p-2 ">
        <Col xs={12} >
          <Navigation>{}</Navigation>
        </Col>
      </Row>
      <Row>
        <Col md={{ span: 4, offset: 4 }}>
          <Form onSubmit={onSubmit} >
            <Form.Group className="mb-3" controlId="formBasicEmail">
              <Form.Label>Username</Form.Label>
              <Form.Control type="text" placeholder="Enter username" onChange={(e: ChangeEvent<HTMLInputElement>) => setUserName(e.currentTarget.value)}/>
            </Form.Group>

           
            <Button variant="secondary" type="submit" >
              Register
            </Button>
          </Form>
        </Col>
      </Row>
    </Container>
  )
}