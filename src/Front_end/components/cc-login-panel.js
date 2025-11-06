// /Users/tuanpham/Chinese-chess-nework-programming/src/Front_end/components/cc-login-panel.js
class CCLoginPanel extends HTMLElement{
	static get observedAttributes(){ return ['visible']; }
	constructor(){
		super();
		const root = this.attachShadow({mode:'open'});
        this.$titleImg = null;
        this.$titleText = null;
		root.innerHTML = `
			<style>
				:host{
					display: block;
					position: absolute;
					top: 35%;
					left: 50%;
					transform: translate(-50%, -50%);
					width: calc(var(--w, 600px) * var(--scale));
					padding: calc(40px * var(--scale));
					background: transparent;
					border-radius: calc(20px * var(--scale));
					opacity: 0;
					pointer-events: none;
					transition: opacity .4s ease;
					z-index: 100;
				}
				:host([visible="true"]){
					opacity: 1;
					pointer-events: auto;
				}
				.title{
					text-align: center;
					color: #000;
					font-size: calc(36px * var(--scale));
					font-weight: bold;
					font-family: serif;
					margin-bottom: calc(40px * var(--scale));
				}
				.title-img{
					display: block;
					width: 100%;
					height: auto;
					margin: 0 auto;
				}
				.form{
					display: flex;
					flex-direction: column;
					align-items: center;
				}
				.inputs-row{
					display: flex;
					flex-direction: column;
					flex: 1;
					margin-right: calc(20px * var(--scale));
				}
				.inputs-button-row{
					display: flex;
					align-items: center;
					width: 100%;
					margin-bottom: calc(8px * var(--scale));
				}
				.links{
					width: 100%;
					display: flex;
					justify-content: space-between;
					margin-top: calc(8px * var(--scale));
				}
			</style>
			<div class="title" part="title">
				<img class="title-img" id="titleImg" style="display:none;" />
				<span id="titleText">Log in</span>
			</div>
			<div class="form" part="form">
				<div class="inputs-button-row">
					<div class="inputs-row">
						<slot name="inputs"></slot>
					</div>
					<slot name="button"></slot>
				</div>
				<div class="links" part="links">
					<slot name="links"></slot>
				</div>
			</div>
		`;
	}
	connectedCallback(){
		document.addEventListener('cc-button-click', this.#handleButtonClick);
		document.addEventListener('cc-login-close', this.#handleClose);
        this.$titleImg = this.shadowRoot.querySelector('#titleImg');
        this.$titleText = this.shadowRoot.querySelector('#titleText');
        this.#syncTitle();
	}
    #syncTitle(){
        const titleSrc = this.getAttribute('title-src');
        if(titleSrc && this.$titleImg && this.$titleText){
            this.$titleImg.src = titleSrc;
            this.$titleImg.style.display = 'block';
            this.$titleText.style.display = 'none';
        } else if(this.$titleImg && this.$titleText){
            this.$titleImg.style.display = 'none';
            this.$titleText.style.display = 'block';
        }
    }
	disconnectedCallback(){
		document.removeEventListener('cc-button-click', this.#handleButtonClick);
		document.removeEventListener('cc-login-close', this.#handleClose);
	}
	attributeChangedCallback(name, oldVal, newVal){
		if(name === 'visible') this.#updateVisibility();
	}
	#handleButtonClick = (e) => {
		if(e.detail.text === 'login'){
			this.setAttribute('visible', 'true');
		}
	}
	#handleClose = () => {
		this.setAttribute('visible', 'false');
	}
	#updateVisibility(){
		if(this.getAttribute('visible') === 'true'){
			document.dispatchEvent(new CustomEvent('cc-login-opened', { bubbles: true }));
		} else {
			document.dispatchEvent(new CustomEvent('cc-login-closed', { bubbles: true }));
		}
	}
}
customElements.define('cc-login-panel', CCLoginPanel);