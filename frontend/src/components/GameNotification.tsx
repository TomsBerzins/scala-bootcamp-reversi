import React, { useState } from 'react';
import Col from 'react-bootstrap/Col';
import Row from 'react-bootstrap/Row';
import Toast from 'react-bootstrap/Toast';

export default function GameNotification({message} : {message:string}) {

    const [show, setShow] = useState(true);

    return (
        <Row>
            <Col>
                <Toast onClose={() => setShow(false)} show={show} autohide>
                    <Toast.Header>
                        <strong className="me-auto">Notification</strong>
                    </Toast.Header>
                    <Toast.Body>{message}</Toast.Body>
                </Toast>
            </Col>
        </Row>
    )
}