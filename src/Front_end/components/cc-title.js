// /Users/tuanpham/Chinese-chess-nework-programming/src/Front_end/components/cc-title.js
class CCTitle extends HTMLElement{
	static get observedAttributes(){ return ['src','alt','state']; }
	constructor(){
		super();
		const root = this.attachShadow({mode:'open'});
		root.innerHTML = `
			<style>
				:host{
					display:block;
					position: absolute;
					top: calc(var(--top, 0px) * var(--scale));
					left: calc(var(--left, 0px) * var(--scale));
					width: calc(var(--w, 600px) * var(--scale));
					height: calc(var(--h, auto) * var(--scale));
					transition: all .5s cubic-bezier(0.4, 0, 0.2, 1);
				}
				:host([state="small"]){
					top: calc(var(--top-small, 0px) * var(--scale));
					left: calc(var(--left-small, 0px) * var(--scale));
					width: calc(var(--w-small, 400px) * var(--scale));
					height: calc(var(--h-small, auto) * var(--scale));
				}
				img{ 
					width:100%; 
					height:100%; 
					object-fit:contain; 
				}
			</style>
			<img class="cc-img" part="img" />
		`;
		this.$img = root.querySelector('img');
	}
	connectedCallback(){ 
		this.#sync();
		document.addEventListener('cc-board-clicked', this.#handleBoardClick);
		document.addEventListener('cc-board-reset', this.#handleBoardReset);
		document.addEventListener('cc-login-opened', this.#handleLoginOpened);
		document.addEventListener('cc-login-closed', this.#handleLoginClosed);
	}
	disconnectedCallback(){
		document.removeEventListener('cc-board-clicked', this.#handleBoardClick);
		document.removeEventListener('cc-board-reset', this.#handleBoardReset);
		document.removeEventListener('cc-login-opened', this.#handleLoginOpened);
		document.removeEventListener('cc-login-closed', this.#handleLoginClosed);
	}
	attributeChangedCallback(){ this.#sync(); }
	#sync(){
		if(this.$img){
			this.$img.src = this.getAttribute('src') || '';
			this.$img.alt = this.getAttribute('alt') || '';
		}
	}
	#handleBoardClick = () => {
		if(this.getAttribute('state') !== 'small'){
			this.setAttribute('state', 'small');
		}
	}
	#handleBoardReset = () => {
		if(this.getAttribute('state') === 'small'){
			this.removeAttribute('state');
		}
	}
	#handleLoginOpened = () => {
		this.style.display = 'none';
	}
	#handleLoginClosed = () => {
		this.style.display = 'block';
	}
}
customElements.define('cc-title', CCTitle);