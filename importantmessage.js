function toggleMessage() {
  const message = document.querySelector('.unread-message');
  message.classList.toggle('active');
}

function dismissMessage(event) {
  event.stopPropagation(); // Prevent triggering toggleMessage
  const message = document.querySelector('.unread-message');
  message.style.display = 'none';
}