// /Users/tuanpham/Chinese-chess-nework-programming/src/Front_end/components/cc-auth-panel.js
class CCAuthPanel extends HTMLElement{
	static get observedAttributes(){ return ['visible']; }
	constructor(){
		super();
		const root = this.attachShadow({mode:'open'});
		root.innerHTML = `
			<style>
				:host{
					display: block;
					position: absolute;
					top: calc(var(--top, 400px) * var(--scale));
					left: 50%;
					transform: translateX(-50%);
					opacity: 0;
					pointer-events: none;
					transition: opacity .4s ease;
				}
				:host([visible="true"]){
					opacity: 1;
					pointer-events: auto;
				}
				.container{
					display: flex;
					flex-direction: column;
					gap: calc(20px * var(--scale));
					align-items: center;
				}
			</style>
			<div class="container" part="container">
				<slot></slot>
			</div>
		`;
	}
	connectedCallback(){
		document.addEventListener('cc-board-clicked', this.#handleBoardClick);
		document.addEventListener('cc-board-reset', this.#handleBoardReset);
	}
	disconnectedCallback(){
		document.removeEventListener('cc-board-clicked', this.#handleBoardClick);
		document.removeEventListener('cc-board-reset', this.#handleBoardReset);
	}
	attributeChangedCallback(name, oldVal, newVal){
		if(name === 'visible') this.#updateVisibility();
	}
	#handleBoardClick = () => {
		this.setAttribute('visible', 'true');
	}
	#handleBoardReset = () => {
		this.setAttribute('visible', 'false');
	}
	#updateVisibility(){
		// Already handled by CSS :host([visible])
	}
}
customElements.define('cc-auth-panel', CCAuthPanel);